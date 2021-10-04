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

import static org.modelingvalue.gradle.mvgplugin.Util.getTestMarker;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
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
import org.jetbrains.annotations.NotNull;

public class MvgBranchBasedBuilder {
    public static final Logger LOGGER                     = Info.LOGGER;
    public static final String BRANCH_INDICATOR           = "-BRANCHED";
    public static final String SNAPSHOT_VERSION_POST      = "-SNAPSHOT";
    public static final String SNAPSHOTS_REPO_POST        = "-snapshots";
    public static final String SNAPSHOTS_GROUP_PRE        = "snapshots.";
    public static final String BRANCH_REASON              = "using Branch Based Building";
    public static final int    MAX_BRANCHNAME_PART_LENGTH = 16;

    private final    Gradle      gradle;
    private final    boolean     isCI;
    private final    boolean     isMaster;
    private volatile String      bbbIdCache;
    private final    Set<String> dependencies = new HashSet<>();
    private final    Set<String> publications = new HashSet<>();

    public MvgBranchBasedBuilder(Gradle gradle) {
        this.gradle = gradle;

        isCI = Info.CI;
        isMaster = InfoGradle.isMasterBranch();

        LOGGER.info("+ bbb: creating MvgBranchBasedBuilder (CI={} master={})", isCI, isMaster);
        TRACE.report(gradle);

        adjustDependencies();
        adjustPublications();

        gradle.addListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                if (result.getFailure() == null) {
                    DependenciesRepoManager dependencyRepoManager = new DependenciesRepoManager(gradle);
                    dependencyRepoManager.saveDependencies(dependencies);
                    dependencyRepoManager.trigger(publications);
                }
            }
        });
    }

    private void adjustDependencies() {
        String negTestMarker = getTestMarker("r-");
        String posTestMarker = getTestMarker("r+");
        gradle.allprojects(p -> {
                    String projectName = String.format("%-30s", p.getName());
                    p.getConfigurations().all(conf -> {
                                String confName = String.format("%-30s", conf.getName());
                                conf.resolutionStrategy(strategy ->
                                        strategy.dependencySubstitution(depSubs ->
                                                depSubs.all(depSub -> {
                                                            ComponentSelector component = depSub.getRequested();
                                                            if (!(component instanceof ModuleComponentSelector)) {
                                                                LOGGER.info("+ bbb: {} {} can not handle unknown dependency class: {}", projectName, confName, component.getClass());
                                                            } else {
                                                                ModuleComponentSelector moduleComponent = (ModuleComponentSelector) component;
                                                                if (!moduleComponent.getVersion().endsWith(BRANCH_INDICATOR)) {
                                                                    LOGGER.info("+ bbb: {} {} {} no BRANCH dependency: {}", negTestMarker, projectName, confName, component);
                                                                } else {
                                                                    String g           = makeBbbGroup(moduleComponent.getGroup());
                                                                    String a           = makeBbbArtifact(moduleComponent.getModule());
                                                                    String v           = makeBbbVersion(moduleComponent.getVersion().replaceAll(Pattern.quote(BRANCH_INDICATOR) + "$", ""));
                                                                    String replacement = g + ":" + a + ":" + v;
                                                                    LOGGER.info("+ bbb: {} {} {} BRANCH dependency, replaced: {} => {}", posTestMarker, projectName, confName, component, replacement);
                                                                    depSub.useTarget(replacement, BRANCH_REASON);

                                                                    dependencies.add(moduleComponent.getGroup() + "." + moduleComponent.getModule());
                                                                }
                                                            }
                                                        }
                                                )
                                        )
                                );
                            }
                    );
                }
        );
    }

    private void adjustPublications() {
        gradle.afterProject(p -> {
            LOGGER.info("+ bbb: running adjustPublications on project {}", p.getName());
            PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
            if (publishing != null) {
                if (!publishing.getRepositories().isEmpty()) {
                    LOGGER.warn("The repository set for project {} is not empty; bbb will not set the proper publish repo (local-maven or github-mvg-packages). Make it empty to activate bbb publishing.", p.getName());
                }
                if (!(isCI && isMaster)) {
                    makePublicationsBbb(publishing);
                }
                if (isCI) {
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
                    LOGGER.info("+ bbb: changed publication {}: '{}:{}:{}' => '{}:{}:{}'",
                            mpub.getName(), oldGroup, oldArtifact, oldVersion, newGroup, newArtifact, newVersion);

                    mpub.setGroupId(newGroup);
                    mpub.setArtifactId(newArtifact);
                    mpub.setVersion(newVersion);
                    TRACE.report(publishing);

                    publications.add(oldGroup + "." + oldArtifact);
                }
            }
        });
    }

    private void publishToMavenLocal(PublishingExtension publishing) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding mavenLocal publishing repo...");
            publishing.getRepositories().mavenLocal();
            TRACE.report(publishing);
        }
    }

    private void publishToGitHub(PublishingExtension publishing, boolean master) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ bbb: adding github publishing repo: {}", master ? "master" : "snapshots");
            Action<MavenArtifactRepository> maker = InfoGradle.getGithubMavenRepoMaker(InfoGradle.isMasterBranch());
            publishing.getRepositories().maven(maker);
            TRACE.report(publishing);
        }
    }

    private String makeBbbGroup(String g) {
        return (isCI && isMaster) || g.length() == 0 || g.startsWith(SNAPSHOTS_GROUP_PRE) ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a) {
        return a;
    }

    private String makeBbbVersion(String v) {
        return (isCI && isMaster) || v.length() == 0 || v.endsWith(SNAPSHOT_VERSION_POST) ? v : cachedBbbId();
    }

    private String cachedBbbId() {
        if (bbbIdCache == null) {
            synchronized (gradle) {
                if (bbbIdCache == null) {
                    String branch    = InfoGradle.getBranch();
                    int    hash      = branch.hashCode();
                    String sanatized = branch.replaceFirst("@.*", "").replaceAll("\\W", "_");
                    String part      = sanatized.substring(0, Math.min(sanatized.length(), MAX_BRANCHNAME_PART_LENGTH));
                    bbbIdCache = String.format("%s-%08x%s", part, hash, SNAPSHOT_VERSION_POST);
                }
            }
        }
        return bbbIdCache;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static class TRACE {
        private static void report(Gradle gradle) {
            if (LOGGER.isDebugEnabled()) {
                gradle.allprojects(p -> {
                    PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
                    LOGGER.debug("+ bbb ---------------------------[ proj={}", p.getName());
                    if (publishing == null) {
                        LOGGER.debug("+      bbb publishing==null");
                    } else {
                        publishing.getPublications().forEach(x -> LOGGER.debug("+      bbb PUBL {}: {}", x.getName(), describe(x)));
                        publishing.getRepositories().forEach(x -> LOGGER.debug("+      bbb REPO {}: {}", x.getName(), describe(x)));
                    }
                    LOGGER.debug("+ bbb ---------------------------] proj={}", p.getName());
                });
            }
        }

        private static void report(PublishingExtension publishing) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("+ bbb ---------------------------[");
                if (publishing == null) {
                    LOGGER.debug("+      bbb publishing==null");
                } else {
                    publishing.getPublications().forEach(x -> LOGGER.debug("+      bbb PUBL {}: {}", x.getName(), describe(x)));
                    publishing.getRepositories().forEach(x -> LOGGER.debug("+      bbb REPO {}: {}", x.getName(), describe(x)));
                }
                LOGGER.debug("+ bbb ---------------------------]");
            }
        }

        private static String describe(Publication x) {
            if (x instanceof MavenPublication) {
                MavenPublication xx = (MavenPublication) x;
                return xx.getGroupId() + ":" + xx.getArtifactId() + ":" + xx.getVersion();
            } else {
                return otherClass("publication", x);
            }
        }

        private static String describe(ArtifactRepository x) {
            if (x instanceof MavenArtifactRepository) {
                return describe((MavenArtifactRepository) x);
            } else {
                return otherClass("artifactRepo", x);
            }
        }

        private static String describe(MavenArtifactRepository mar) {
            if (mar == null) {
                return "<null>";
            } else {
                String credeantials = describe(mar.getCredentials());
                String authentications = mar.getAuthentication().getAsMap().entrySet().stream().map(e -> {
                    Authentication au = e.getValue();
                    if (!(au instanceof AuthenticationInternal)) {
                        return e.getKey() + ":" + otherClass("authentication", au);
                    } else {
                        AuthenticationInternal aui         = (AuthenticationInternal) au;
                        Credentials            credentials = aui.getCredentials();
                        if (!(credentials instanceof PasswordCredentials)) {
                            return e.getKey() + ":" + aui.getName() + ":" + otherClass("credentials", credentials);
                        } else {
                            return e.getKey() + ":" + aui.getName() + ":" + describe((PasswordCredentials) credentials);
                        }
                    }
                }).collect(Collectors.joining(", ", "Authentications[", "]"));
                return "repo url=" + mar.getUrl() + " - " + credeantials + " - " + authentications;
            }
        }

        @NotNull
        private static String describe(PasswordCredentials pwcr) {
            return pwcr.getUsername() + ":" + Util.hide(pwcr.getPassword());
        }

        private static String otherClass(String name, Object o) {
            return name + "???" + (o == null ? "<null>" : o.getClass());
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
