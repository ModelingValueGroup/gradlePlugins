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

import static org.modelingvalue.gradle.mvgplugin.Info.MVG_MAVEN_REPO_URL;
import static org.modelingvalue.gradle.mvgplugin.Util.envOrProp;

import java.net.URI;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;

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
        debug_report(gradle);

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
                    debug_report(publishing);
                }
            }
        });
    }

    private void publishToMavenLocal(PublishingExtension publishing) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding mavenLocal publishing repo...");
            publishing.getRepositories().mavenLocal();
            debug_report(publishing);
        }
    }

    private void publishToGitHub(PublishingExtension publishing, boolean master) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding {}-MVG-github publishing repo...", master ? "master" : "snapshot");
            publishing.getRepositories().maven(mar -> {
                URI url = URI.create(MVG_MAVEN_REPO_URL);
                if (!master) {
                    if (envOrProp("TOMTOMTOM", null) != null) {
                        LOGGER.info("TOMTOMTOM skipping makeBbbRepo({})", url);
                    } else {
                        url = makeBbbRepo(url);
                    }
                }
                mar.setUrl(url);
                mar.credentials(c -> {
                    c.setUsername("");
                    c.setPassword(Info.ALLREP_TOKEN);
                });
            });
            debug_report(publishing);
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
    private void debug_report(Gradle gradle) {
        if (LOGGER.isDebugEnabled()) {
            gradle.allprojects(p -> {
                PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
                LOGGER.debug("+ bbb ---------------------------[ proj={}", p.getName());
                if (publishing == null) {
                    LOGGER.debug("+      bbb publishing==null");
                } else {
                    publishing.getPublications().forEach(x -> LOGGER.debug("+      bbb PUBL {}: {}", x.getName(), debug_describe(x)));
                    publishing.getRepositories().forEach(x -> LOGGER.debug("+      bbb REPO {}: {}", x.getName(), debug_describe(x)));
                }
                LOGGER.debug("+ bbb ---------------------------] proj={}", p.getName());
            });
        }
    }

    private void debug_report(PublishingExtension publishing) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("+ bbb ---------------------------[");
            if (publishing == null) {
                LOGGER.debug("+      bbb publishing==null");
            } else {
                publishing.getPublications().forEach(x -> LOGGER.debug("+      bbb PUBL {}: {}", x.getName(), debug_describe(x)));
                publishing.getRepositories().forEach(x -> LOGGER.debug("+      bbb REPO {}: {}", x.getName(), debug_describe(x)));
            }
            LOGGER.debug("+ bbb ---------------------------]");
        }
    }

    private String debug_describe(Publication x) {
        if (x instanceof MavenPublication) {
            MavenPublication xx = (MavenPublication) x;
            return xx.getGroupId() + ":" + xx.getArtifactId() + ":" + xx.getVersion();
        } else {
            return debug_otherClass("publication", x);
        }
    }

    private String debug_describe(ArtifactRepository x) {
        if (x instanceof MavenArtifactRepository) {
            MavenArtifactRepository xx = (MavenArtifactRepository) x;
            return "repo url=" + xx.getUrl() + " - " + debug_describe(xx.getAuthentication());
        } else {
            return debug_otherClass("artifactRepo", x);
        }
    }

    private String debug_describe(AuthenticationContainer container) {
        return container.getAsMap().entrySet().stream().map(e -> {
            Authentication au = e.getValue();
            if (au instanceof AuthenticationInternal) {
                AuthenticationInternal aui         = (AuthenticationInternal) au;
                Credentials            credentials = aui.getCredentials();
                if (credentials instanceof PasswordCredentials) {
                    PasswordCredentials pwcr = (PasswordCredentials) credentials;
                    return e.getKey() + ":" + aui.getName() + ":" + pwcr.getUsername() + ":" + Util.hide(pwcr.getPassword());
                } else {
                    return e.getKey() + ":" + aui.getName() + ":" + debug_otherClass("credentials", credentials);
                }
            } else {
                return e.getKey() + ":" + debug_otherClass("authentication", au);
            }
        }).collect(Collectors.joining(", ", "Authentications[", "]"));
    }

    private String debug_otherClass(String name, Object o) {
        return name + "???" + (o == null ? "<null>" : o.getClass());
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
