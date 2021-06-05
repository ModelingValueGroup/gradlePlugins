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

package org.modelingvalue.gradle.mvgplugin;

import java.net.URI;
import java.util.regex.Pattern;

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

public class MvgBranchBasedBuilder {
    public static final Logger LOGGER                     = Info.LOGGER;
    public static final String BRANCH_INDICATOR           = "-BRANCHED";
    public static final String SNAPSHOT_VERSION_POST      = "-SNAPSHOT";
    public static final String SNAPSHOTS_REPO_POST        = "-snapshots";
    public static final String SNAPSHOTS_GROUP_PRE        = "snapshots.";
    public static final String BRANCH_REASON              = "using Branch Based Building";
    public static final int    MAX_BRANCHNAME_PART_LENGTH = 16;

    private final Gradle  gradle;
    private final boolean ci;
    private final boolean isMaster;

    public MvgBranchBasedBuilder(Gradle gradle) {
        this.gradle = gradle;

        ci = Info.CI;
        isMaster = Info.isMasterBranch(gradle);

        LOGGER.info("+ bbb: creating MvgBranchBasedBuilder (ci={} master={})", ci, isMaster);
        TOMTOMTOM_report(gradle);

        adjustDependencies();
        adjustPublications(gradle);
    }

    private void adjustDependencies() {
        gradle.allprojects(p ->
                p.getConfigurations().all(conf ->
                        conf.resolutionStrategy(strategy ->
                                strategy.dependencySubstitution(depSubs ->
                                        depSubs.all(depSub -> {
                                                    ComponentSelector component = depSub.getRequested();
                                                    if (component instanceof ModuleComponentSelector) {
                                                        checkReplacement(depSub, (ModuleComponentSelector) component);
                                                    } else {
                                                        LOGGER.info("+ bbb: can not handle unknown dependency class: " + component.getClass());
                                                    }
                                                }
                                        )
                                )
                        )
                )
        );
    }

    private void checkReplacement(DependencySubstitution depSub, ModuleComponentSelector component) {
        String version = component.getVersion();
        if (version.endsWith(BRANCH_INDICATOR)) {
            String replacement = makeBbbGroup(component.getGroup()) + ":" + makeBbbArtifact(component.getModule()) + ":" + makeBbbVersion(version.replaceAll(Pattern.quote(BRANCH_INDICATOR) + "$", ""));
            LOGGER.info("+ bbb: dependency     replaced: " + component + " => " + replacement);
            depSub.useTarget(replacement, BRANCH_REASON);
        } else {
            LOGGER.info("+ bbb: dependency NOT replaced: " + component);
        }
    }

    private void adjustPublications(Gradle gradle) {
        gradle.afterProject(p -> {
            LOGGER.info("+ bbb: running adjustPublications on project {}", p.getName());
            PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
            if (publishing != null) {
                if (!publishing.getRepositories().isEmpty()) {
                    LOGGER.warn("The repository set for project {} is not empty; bbb will not set the proper publish repo (local-maven or github-mvg-packages). Make it empty to activate bbb publishing.", p.getName());
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
                    LOGGER.info("+ bbb: changed publication {}: '{}' => '{}'",
                            mpub.getName(),
                            oldGroup + ":" + oldArtifact + ":" + oldVersion,
                            newGroup + ":" + newArtifact + ":" + newVersion);

                    mpub.setGroupId(newGroup);
                    mpub.setArtifactId(newArtifact);
                    mpub.setVersion(newVersion);
                    TOMTOMTOM_report(publishing);
                }
            }
        });
    }

    private void publishToMavenLocal(PublishingExtension publishing) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding mavenLocal publishing repo...");
            publishing.getRepositories().mavenLocal();
            TOMTOMTOM_report(publishing);
        }
    }

    private void publishToGitHub(PublishingExtension publishing, boolean master) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding {}-MVG-github publishing repo...", master ? "master" : "snapshot");
            publishing.getRepositories().maven(mar -> {
                URI url = URI.create("https://maven.pkg.github.com/ModelingValueGroup/packages");
                if (!master) {
                    url = makeBbbRepo(url);
                }
                mar.setUrl(url);
                mar.credentials(c -> {
                    c.setUsername("");
                    c.setPassword(Info.ALLREP_TOKEN);
                });
            });
            TOMTOMTOM_report(publishing);
        }
    }

    private String makeBbbGroup(String g) {
        return (ci && isMaster) || g.length() == 0 || g.startsWith(SNAPSHOTS_GROUP_PRE) ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a) {
        return a;
    }

    private String makeBbbVersion(String v) {
        return (ci && isMaster) || v.length() == 0 || v.endsWith(SNAPSHOT_VERSION_POST) ? v : cachedBbbId();
    }

    private URI makeBbbRepo(URI u) {
        String s = u.toString().replaceAll("/$", "");
        return s.length() == 0 || s.endsWith(SNAPSHOTS_REPO_POST) ? u : Util.makeURL(s + SNAPSHOTS_REPO_POST);
    }

    private String bbbId;

    private String cachedBbbId() {
        if (bbbId == null) {
            String branch    = Info.getBranch(gradle);
            int    hash      = branch.hashCode();
            String sanatized = branch.replaceFirst("@.*", "").replaceAll("\\W", "_");
            String part      = sanatized.substring(0, Math.min(sanatized.length(), MAX_BRANCHNAME_PART_LENGTH));
            bbbId = String.format("%s-%08x%s", part, hash, SNAPSHOT_VERSION_POST);
        }
        return bbbId;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void TOMTOMTOM_report(Gradle gradle) {
        if (LOGGER.isTraceEnabled()) {
            gradle.allprojects(p -> {
                PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
                LOGGER.trace("+ bbb ---------------------------[ proj={}", p.getName());
                if (publishing == null) {
                    LOGGER.trace("+      bbb publishing==null");
                } else {
                    publishing.getPublications().forEach(x -> LOGGER.trace("+      bbb PUBL {}: {}", x.getName(), TOMTOMTOM_describe(x)));
                    publishing.getRepositories().forEach(x -> LOGGER.trace("+      bbb REPO {}: {}", x.getName(), TOMTOMTOM_describe(x)));
                }
                LOGGER.trace("+ bbb ---------------------------] proj={}", p.getName());
            });
        }
    }

    private void TOMTOMTOM_report(PublishingExtension publishing) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("+ bbb ---------------------------[");
            if (publishing == null) {
                LOGGER.trace("+      bbb publishing==null");
            } else {
                publishing.getPublications().forEach(x -> LOGGER.trace("+      bbb PUBL {}: {}", x.getName(), TOMTOMTOM_describe(x)));
                publishing.getRepositories().forEach(x -> LOGGER.trace("+      bbb REPO {}: {}", x.getName(), TOMTOMTOM_describe(x)));
            }
            LOGGER.trace("+ bbb ---------------------------]");
        }
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
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
