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
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.Publication;
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

    private void TOMTOMTOM_report(Project project) {
        PublishingExtension publishing = (PublishingExtension) project.getExtensions().findByName("publishing");
        LOGGER.info("+ bbb ---------------------------[ proj={}", project.getName());
        if (publishing == null) {
            LOGGER.info("+      bbb publishing==null");
        } else {
            publishing.getRepositories().forEach(x -> LOGGER.info("+      bbb REPO {}: {}", x.getName(), TOMTOMTOM_describe(x)));
            publishing.getPublications().forEach(x -> LOGGER.info("+      bbb PUBL {}: {}", x.getName(), TOMTOMTOM_describe(x)));
        }
        LOGGER.info("+ bbb ---------------------------] proj={}", project.getName());
    }

    private String TOMTOMTOM_describe(Publication x) {
        if (x instanceof MavenPublication) {
            MavenPublication xx = (MavenPublication) x;
            return xx.getGroupId() + ":" + xx.getArtifactId() + ":" + xx.getVersion();
        } else {
            return "IS " + x.getClass();
        }
    }

    private String TOMTOMTOM_describe(ArtifactRepository x) {
        if (x instanceof MavenArtifactRepository) {
            MavenArtifactRepository xx = (MavenArtifactRepository) x;
            return "url=" + xx.getUrl();
        } else {
            return "IS " + x.getClass();
        }
    }

    private void TOMTOMTOM_report(PublishingExtension publishing) {
        LOGGER.info("+ bbb ---------------------------[");
        if (publishing == null) {
            LOGGER.info("+      bbb publishing==null");
        } else {
            publishing.getRepositories().forEach(x -> LOGGER.info("+      bbb REPO {}: {}", x.getName(), TOMTOMTOM_describe(x)));
            publishing.getPublications().forEach(x -> LOGGER.info("+      bbb PUBL {}: {}", x.getName(), TOMTOMTOM_describe(x)));
        }
        LOGGER.info("+ bbb ---------------------------]");
    }

    public BranchBasedBuilder(Project project) {
        gradle = project.getGradle();

        boolean ci       = Info.CI;
        boolean isMaster = Info.isMasterBranch(gradle);

        LOGGER.info("+ bbb: running BranchBasedBuilder on project {} (ci={} master={})", project.getName(), ci, isMaster);
        TOMTOMTOM_report(project);

        if (!(ci && isMaster)) {
            makeAllDependenciesBbb();
        }

        gradle.afterProject(p -> {
            LOGGER.info("+ bbb: running afterProject on project {}", p.getName());
            PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
            if (publishing != null) {
                if (!publishing.getRepositories().isEmpty()) {
                    LOGGER.warn("the repository set is not empty, bbb will not insert the right repo. Make it empty to activate bbb.");
                }
                if (!(ci && isMaster)) {
                    makePublicationsBbb(publishing);
                }
                if (ci) {
                    publishToGitHub(publishing, isMaster);
                } else {
                    publishToMavenLocal(publishing);
                }
            }
        });
    }

    private void publishToMavenLocal(PublishingExtension publishing) {
        if (publishing.getRepositories().isEmpty()) {
            LOGGER.info("+ bbb: adding publishing repo...");
            publishing.getRepositories().mavenLocal();
            TOMTOMTOM_report(publishing);
        }
    }

    private void publishToGitHub(PublishingExtension publishing, boolean master) {
        if (publishing.getRepositories().isEmpty()) {
            LOGGER.info("+ bbb: adding {} repo...", master ? "master" : "snapshot");
            publishing.getRepositories().maven(mar -> {
                URI url = URI.create("https://maven.pkg.github.com/ModelingValueGroup/packages");
                if (!master) {
                    url = makeBbbRepo(url);
                }
                mar.setUrl(url);
                mar.credentials(cr -> {
                    cr.setPassword(Info.ALLREP_TOKEN);
                    cr.setUsername("");
                });
            });
            TOMTOMTOM_report(publishing);
        }
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
                    TOMTOMTOM_report(publishing);
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
        return v.endsWith(SNAPSHOT_VERSION_POST) ? v : cacheBbbId();
    }

    private String bbbId;

    private String cacheBbbId() {
        if (bbbId == null) {
            String branch = Info.getGithubRef(gradle).replaceAll("^refs/heads/", "");
            String part   = branch.replaceAll("\\W", "_");
            bbbId = String.format("%s_%08x_%s", part, branch.hashCode(), SNAPSHOT_VERSION_POST);
        }
        return bbbId;
    }
}
