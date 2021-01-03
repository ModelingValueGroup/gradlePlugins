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

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME;

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
    public static final Logger LOGGER                = Info.LOGGER;
    public static final String BRANCH_INDICATOR      = "-BRANCH";
    public static final String SNAPSHOT_VERSION_POST = "-SNAPSHOT";
    public static final String SNAPSHOTS_REPO_POST   = "-snapshots";
    public static final String SNAPSHOTS_GROUP_PRE   = "snapshots.";
    public static final String REASON                = "using Branch Based Building";

    private final Gradle gradle;

    public BranchBasedBuilder(Project project) {
        gradle = project.getGradle();

        boolean isLocal       = !Info.CI;
        boolean isOtherBranch = !Info.isMasterBranch(gradle);

        if (isLocal) {
            makeAllDependenciesBbb();
        } else if (isOtherBranch) {
            makeAllDependenciesBbb();
        }

        gradle.afterProject(p -> {
            LOGGER.info("+ bbb: running BranchBasedBuilder on project {}", p.getName());
            PublishingExtension publishing = (PublishingExtension) project.getExtensions().findByName("publishing");
            if (publishing != null) {
                if (isLocal) {
                    onlyPublishToMavenLocal(publishing);
                    makePublicationsBbb(publishing);
                } else if (isOtherBranch) {
                    onlyPublishToSnapShots(publishing);
                    makePublicationsBbb(publishing);
                }
            }
        });
    }

    private void onlyPublishToMavenLocal(PublishingExtension publishing) {
        publishing.getRepositories().all(r -> {
            if (publishing.getRepositories().stream().anyMatch(repo -> repo.getName().equals(DEFAULT_MAVEN_LOCAL_REPO_NAME))) {
                LOGGER.info("+ bbb: adding publishing repo {}", DEFAULT_MAVEN_LOCAL_REPO_NAME);
                publishing.getRepositories().mavenLocal();
            }
        });
        publishing.getRepositories().all(r -> {
            if (!r.getName().equals(DEFAULT_MAVEN_LOCAL_REPO_NAME)) {
                LOGGER.info("+ bbb: removing publishing repo {}", r.getName());
                publishing.getRepositories().remove(r);
            }
        });
    }

    private void onlyPublishToSnapShots(PublishingExtension publishing) {
        publishing.getRepositories().all(r -> {
            if (r instanceof MavenArtifactRepository) {
                MavenArtifactRepository mr     = (MavenArtifactRepository) r;
                URI                     oldUrl = mr.getUrl();
                String                  scheme = oldUrl.getScheme();
                if (scheme.startsWith("http")) {
                    URI newUrl = makeBbbRepo(oldUrl);
                    if (!oldUrl.equals(newUrl)) {
                        LOGGER.info("+ bbb: replaced maven publish URL '{}'  in repository {} by '{}'", oldUrl, mr.getName(), newUrl);
                        mr.setUrl(newUrl);
                    }
                }
            }
        });
        publishing.getRepositories().all(r -> {
            if (r.getName().equals(DEFAULT_MAVEN_LOCAL_REPO_NAME)) {
                LOGGER.info("+ bbb: removing publishing repo {}", r.getName());
                publishing.getRepositories().remove(r);
            }
        });
    }

    private void makePublicationsBbb(PublishingExtension publishing) {
        publishing.getPublications().all(pub -> {
            if (pub instanceof MavenPublication) {
                MavenPublication mpub = (MavenPublication) pub;

                String oldGroup    = mpub.getGroupId();
                String oldArtifact = mpub.getArtifactId();
                String oldVersion  = mpub.getVersion();

                String newGroup    = makeBbbGroup(oldGroup);
                String newArtifact = makeBbbArtifact(oldArtifact);
                String newVersion  = makeBbbVersion(oldVersion);

                //noinspection ConstantConditions
                if (!oldGroup.equals(newGroup) || !oldArtifact.equals(newArtifact) || !oldVersion.equals(newVersion)) {
                    LOGGER.info("+ bbb: replaced group    '{}'  in publication {} by '{}'", oldGroup, mpub.getName(), newGroup);
                    LOGGER.info("+ bbb: replaced artifact '{}'  in publication {} by '{}'", oldArtifact, mpub.getName(), newArtifact);
                    LOGGER.info("+ bbb: replaced version  '{}'  in publication {} by '{}'", oldVersion, mpub.getName(), newVersion);

                    mpub.setGroupId(newGroup);
                    mpub.setArtifactId(newArtifact);
                    mpub.setVersion(newVersion);
                }
            }
        });
    }

    private void makeAllDependenciesBbb() {
        gradle.allprojects(p ->
                p.getConfigurations().all(conf ->
                        conf.resolutionStrategy(resolutionStrategy ->
                                resolutionStrategy.getDependencySubstitution().all(depSub -> {
                                    ComponentSelector component = depSub.getRequested();
                                    if (component instanceof ModuleComponentSelector) {
                                        checkReplacement(depSub, (ModuleComponentSelector) component);
                                    }
                                }))));
    }

    private void checkReplacement(DependencySubstitution depSub, ModuleComponentSelector component) {
        String replacement = substitute(component.getGroup(), component.getModule(), component.getVersion());
        if (replacement != null) {
            depSub.useTarget(replacement, REASON);
            LOGGER.info("+ bbb: dependency     replaced: " + component + " => " + replacement);
        } else {
            LOGGER.info("+ bbb: dependency NOT replaced: " + component);
        }
    }

    public String substitute(String g, String a, String v) {
        return !v.endsWith(BRANCH_INDICATOR) ? null : makeBbbGroup(g) + ":" + makeBbbArtifact(a) + ":" + makeBbbVersion(v);
    }

    private URI makeBbbRepo(URI u) {
        String s = u.toString().replaceAll("/$", "");
        return s.endsWith(SNAPSHOTS_REPO_POST) ? u : Util.makeURL(s + SNAPSHOTS_REPO_POST);
    }

    private String makeBbbGroup(String g) {
        return g.startsWith(SNAPSHOTS_GROUP_PRE) ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a) {
        return a;
    }

    private String makeBbbVersion(String v) {
        return v.endsWith(SNAPSHOT_VERSION_POST) ? v : String.format("%08x", Info.getGithubRef(gradle).hashCode()) + SNAPSHOT_VERSION_POST;
    }
}
