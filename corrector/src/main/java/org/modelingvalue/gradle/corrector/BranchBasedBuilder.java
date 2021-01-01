//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.corrector;

import java.net.URI;

import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

public class BranchBasedBuilder {
    public static final Logger LOGGER              = Info.LOGGER;
    public static final String BRANCH_INDICATOR    = "-BRANCH";
    public static final String SNAPSHOT_POST       = "-SNAPSHOT";
    public static final String SNAPSHOTS_GROUP_PRE = "snapshots.";
    public static final String REASON              = "using Branch Based Building";

    public static String replaceOrNull(Gradle gradle, String gav) {
        return new BranchBasedBuilder(gradle).substitute(gav);
    }

    public static String replace(Gradle gradle, String gav) {
        String gavNew = replaceOrNull(gradle, gav);
        return gavNew == null ? gav : gavNew;
    }

    private final Gradle  gradle;
    private final boolean isCiMasterBranch;
    private final String  bbbVersion;

    public BranchBasedBuilder(Project project) {
        this(project.getGradle());
        attach();

        if (!isCiMasterBranch) {
            project.getGradle().afterProject(p -> {
                LOGGER.info("+ running BranchBasedBuilder on project {}", p.getName());
                PublishingExtension publishing = (PublishingExtension) project.getExtensions().findByName("publishing");
                if (publishing != null) {
                    publishing.getRepositories()
                            .stream()
                            .filter(x -> x instanceof MavenArtifactRepository)
                            .map(x -> (MavenArtifactRepository) x)
                            .forEach(mar -> {
                                URI newUrl = makeBbbRepo(mar.getUrl());
                                LOGGER.info("+ replaced maven publish URL '{}'  in repository {} by '{}'", mar.getUrl(), mar.getName(), newUrl);
                                mar.setUrl(newUrl);
                            });
                    publishing.getPublications()
                            .stream()
                            .filter(x -> x instanceof MavenPublication)
                            .map(x -> (MavenPublication) x)
                            .forEach(mp -> {
                                String newGroup    = makeBbbGroup(mp.getGroupId());
                                String newArtifact = makeBbbArtifact(mp.getArtifactId());
                                String newVersion  = makeBbbVersion(mp.getVersion());

                                LOGGER.info("+ replaced group    '{}'  in publication {} by '{}'", mp.getGroupId(), mp.getName(), newGroup);
                                LOGGER.info("+ replaced artifact '{}'  in publication {} by '{}'", mp.getArtifactId(), mp.getName(), newArtifact);
                                LOGGER.info("+ replaced version  '{}'  in publication {} by '{}'", mp.getVersion(), mp.getName(), newVersion);

                                mp.setGroupId(newGroup);
                                mp.setArtifactId(newArtifact);
                                mp.setVersion(newVersion);

                            });
                }
            });
        }
    }

    public BranchBasedBuilder(Gradle gradle) {
        this.gradle = gradle;
        isCiMasterBranch = Info.CI && Info.isMasterBranch(gradle);

        bbbVersion = String.format("%08x", Info.getGithubRef(gradle).hashCode()) + SNAPSHOT_POST;
    }

    public void attach() {
        gradle.allprojects(project ->
                project.getConfigurations().all(conf ->
                        conf.resolutionStrategy(resolutionStrategy ->
                                resolutionStrategy.getDependencySubstitution().all(depSub -> {
                                    ComponentSelector component = depSub.getRequested();
                                    if (component instanceof ModuleComponentSelector) {
                                        checkReplacement(depSub, (ModuleComponentSelector) component);
                                    }
                                }))));
    }

    public String substitute(String gav) {
        String[] parts = gav.split(":");
        return parts.length != 3 ? null : substitute(parts[0], parts[1], parts[2]);
    }

    public String substitute(String g, String a, String v) {
        return !v.endsWith(BRANCH_INDICATOR) ? null : makeBbbGroup(g) + ":" + makeBbbArtifact(a) + ":" + makeBbbVersion(v);
    }

    private void checkReplacement(DependencySubstitution depSub, ModuleComponentSelector component) {
        String replacement = substitute(component.getGroup(), component.getModule(), component.getVersion());
        if (replacement != null) {
            depSub.useTarget(replacement, REASON);
            LOGGER.info("+ dependency     replaced: " + component + " => " + replacement);
        } else {
            LOGGER.info("+ dependency NOT replaced: " + component);
        }
    }

    private URI makeBbbRepo(URI u) {
        return isCiMasterBranch ? u : Util.makeURL(u.toString() + "-snapshots");
    }

    private String makeBbbGroup(String g) {
        return isCiMasterBranch ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a) {
        return isCiMasterBranch ? a : a;
    }

    private String makeBbbVersion(String v) {
        return isCiMasterBranch ? v : bbbVersion;
    }
}
