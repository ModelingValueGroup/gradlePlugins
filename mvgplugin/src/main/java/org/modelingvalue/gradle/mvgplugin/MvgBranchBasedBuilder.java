//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.modelingvalue.gradle.mvgplugin.Info.CI;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMasterBranch;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMvgCI_orTesting;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.jetbrains.annotations.NotNull;

public class MvgBranchBasedBuilder {
    public static final String BRANCH_INDICATOR           = "-BRANCHED";
    public static final String SNAPSHOT_VERSION_POST      = "-SNAPSHOT";
    public static final String SNAPSHOTS_REPO_POST        = "-snapshots";
    public static final String SNAPSHOTS_GROUP_PRE        = "snapshots.";
    public static final String BRANCH_REASON              = "using Branch Based Building";
    public static final int    MAX_BRANCHNAME_PART_LENGTH = 16;

    private final Gradle              gradle;
    private final Map<String, String> bbbIdCache         = new ConcurrentHashMap<>();
    private final Set<String>         dependenciesToSave = new HashSet<>();
    private final Set<String>         publications       = new HashSet<>();

    public MvgBranchBasedBuilder(Gradle gradle) {
        this.gradle = gradle;

        LOGGER.info("+ mvg-bbb: creating MvgBranchBasedBuilder (CI={} TEST|CI={} master={})", CI, isMvgCI_orTesting(), isMasterBranch());
        TRACE.report(gradle);

        adjustAllDependencies();
        adjustAllPublications();

        gradle.addListener(new BuildAdapter() {
            @SuppressWarnings("deprecation")
            @Override
            public void buildFinished(BuildResult result) {
                if (result.getFailure() == null) {
                    DependenciesRepoManager dependencyRepoManager = new DependenciesRepoManager(gradle);
                    dependencyRepoManager.saveDependencies(dependenciesToSave);
                    dependencyRepoManager.trigger(publications);
                }
            }
        });
    }

    private void adjustAllDependencies() {
        gradle.allprojects(p -> p.getConfigurations().all(conf -> adjustDependencies(p, conf)));
    }

    private void adjustDependencies(Project project, Configuration conf) {
        String projectName = String.format("%-30s", project.getName());
        String confName    = String.format("%-30s", conf.getName());
        conf.resolutionStrategy(strategy ->
                strategy.dependencySubstitution(depSubs ->
                        depSubs.all(depSub -> {
                                    ComponentSelector selector = depSub.getRequested();
                                    if (!(selector instanceof ModuleComponentSelector)) {
                                        LOGGER.info("+ mvg-bbb: {} {} can not handle unknown dependency class: {}", projectName, confName, selector.getClass());
                                    } else {
                                        ModuleComponentSelector moduleComponent = (ModuleComponentSelector) selector;
                                        if (!moduleComponent.getVersion().endsWith(BRANCH_INDICATOR)) {
                                            LOGGER.info("+ mvg-bbb: {} {} {} no {} dependency: {}", Util.TEST_MARKER_REPLACE_NOT_DONE, projectName, confName, BRANCH_INDICATOR, selector);
                                        } else {
                                            String       rawGroup    = moduleComponent.getGroup();
                                            String       rawArtifact = moduleComponent.getModule();
                                            String       rawVersion  = moduleComponent.getVersion().replaceAll(Pattern.quote(BRANCH_INDICATOR) + "$", "");
                                            List<String> branchesToTry;
                                            if (InfoGradle.isMasterBranch()) {
                                                branchesToTry = List.of(InfoGradle.getBranch());
                                            } else if (InfoGradle.isDevelopBranch()) {
                                                branchesToTry = List.of(InfoGradle.getBranch(), "master");
                                            } else {
                                                branchesToTry = List.of(InfoGradle.getBranch(), "develop", "master");
                                            }
                                            for (String branch : branchesToTry) {
                                                String newGAV = makeBbbGAV(branch, rawGroup, rawArtifact, rawVersion);

                                                if (!branch.equals("master") && !isInAnyMavenRepo(project, newGAV)) {
                                                    LOGGER.info("+ mvg-bbb: {} {} {} {} dependency not found in branch {}, skipped : {} => {}", Util.TEST_MARKER_REPLACE_DONE, projectName, confName, BRANCH_INDICATOR, branch, selector, newGAV);
                                                } else {
                                                    LOGGER.info("+ mvg-bbb: {} {} {} {} dependency     found in branch {}, replaced: {} => {}", Util.TEST_MARKER_REPLACE_DONE, projectName, confName, BRANCH_INDICATOR, branch, selector, newGAV);
                                                    depSub.useTarget(newGAV, BRANCH_REASON);
                                                    dependenciesToSave.add(rawGroup + "." + rawArtifact);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                )
        );
    }

    private boolean isInAnyMavenRepo(Project project, String gav) {
        try {
            LOGGER.info("+ mvg-bbb: checking if in any repo: {}", gav);
            Dependency    dependency    = project.getDependencies().create(gav);
            Configuration configuration = project.getConfigurations().detachedConfiguration(dependency);
            adjustDependencies(project, configuration);
            Set<File> resolved = configuration.resolve();
            boolean   found    = !resolved.isEmpty();
            LOGGER.info("+ mvg-bbb: for {} I found: {}", gav, resolved);
            return found;
        } catch (Throwable e) {
            LOGGER.error("problem trying to find '" + gav + "' in any repo", e);
            return false;
        }
    }

    private void adjustAllPublications() {
        // TODO disable all publicatons when: github.actor == "dependabot[bot]"
        gradle.afterProject(p -> {
            LOGGER.info("+ mvg-bbb: running adjustPublications on project {}", p.getName());
            PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
            if (publishing != null) {
                if (!publishing.getRepositories().isEmpty()) {
                    LOGGER.warn("The repository set for project {} is not empty; bbb will not set the proper publish repo (local-maven or github-mvg-packages). Make it empty to activate bbb publishing.", p.getName());
                }
                // only publish as-is if not master-CI, otherwise adjust the publications into snapshots
                if (isMvgCI_orTesting()) {
                    if (!isMasterBranch()) {
                        makePublicationsBbb(publishing);
                    }
                    publishToGitHub(publishing);
                } else {
                    makePublicationsBbb(publishing);
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
                String oldGAV      = makeGAV(oldGroup, oldArtifact, oldVersion);

                String branch      = InfoGradle.getBranch();
                String newGroup    = makeBbbGroup(oldGroup, branch);
                String newArtifact = makeBbbArtifact(oldArtifact, branch);
                String newVersion  = makeBbbVersion(oldVersion, branch);
                String newGAV      = makeGAV(newGroup, newArtifact, newVersion);

                if (!oldGAV.equals(newGAV)) {
                    LOGGER.info("+ mvg-bbb: changed publication {}: '{}' => '{}'", mpub.getName(), oldGAV, newGAV);

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
            LOGGER.info("+ mvg-bbb: adding mavenLocal publishing repo...");
            publishing.getRepositories().mavenLocal();
            TRACE.report(publishing);
        }
    }

    private void publishToGitHub(PublishingExtension publishing) {
        if (publishing.getRepositories().isEmpty() && !publishing.getPublications().isEmpty()) {
            LOGGER.info("+ mvg-bbb: adding github publishing repo: {}", isMasterBranch() ? "master" : "snapshots");
            Action<MavenArtifactRepository> maker = InfoGradle.getGithubMavenRepoMaker(isMasterBranch());
            publishing.getRepositories().maven(maker);
            TRACE.report(publishing);
        }
    }

    private String makeBbbGAV(String branch, String g, String a, String v) {
        return makeGAV(makeBbbGroup(g, branch), makeBbbArtifact(a, branch), makeBbbVersion(v, branch));
    }

    private String makeGAV(String g, String a, String v) {
        return String.format("%s:%s:%s", g, a, v);
    }

    private String makeBbbGroup(String g, String branch) {
        return (isMvgCI_orTesting() && isMasterBranch(branch)) || g.length() == 0 || g.startsWith(SNAPSHOTS_GROUP_PRE) ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a, @SuppressWarnings("unused") String branch) {
        return a;
    }

    private String makeBbbVersion(String v, String branch) {
        if ((isMvgCI_orTesting() && isMasterBranch(branch)) || v.length() == 0 || v.endsWith(SNAPSHOT_VERSION_POST)) {
            return v;
        } else {
            return bbbIdCache.computeIfAbsent(branch, b -> {
                int    hash      = b.hashCode();
                String sanatized = b.replaceFirst("@.*", "").replaceAll("\\W", "_");
                String part      = sanatized.substring(0, Math.min(sanatized.length(), MAX_BRANCHNAME_PART_LENGTH));
                return String.format("%s-%08x%s", part, hash, SNAPSHOT_VERSION_POST);
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static class TRACE {
        private static void report(Gradle gradle) {
            if (LOGGER.isDebugEnabled()) {
                gradle.allprojects(p -> {
                    PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
                    LOGGER.debug("++ mvg-bbb: ---------------------------[ proj={}", p.getName());
                    if (publishing == null) {
                        LOGGER.debug("++ mvg-bbb:     bbb publishing==null");
                    } else {
                        publishing.getPublications().forEach(x -> LOGGER.debug("++ mvg-bbb:     bbb PUBL {}: {}", x.getName(), describe(x)));
                        publishing.getRepositories().forEach(x -> LOGGER.debug("++ mvg-bbb:     bbb REPO {}: {}", x.getName(), describe(x)));
                    }
                    LOGGER.debug("++ mvg-bbb: ---------------------------] proj={}", p.getName());
                });
            }
        }

        private static void report(PublishingExtension publishing) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("++ mvg-bbb: ---------------------------[");
                if (publishing == null) {
                    LOGGER.debug("++ mvg-bbb:     bbb publishing==null");
                } else {
                    publishing.getPublications().forEach(x -> LOGGER.debug("++ mvg-bbb:     bbb PUBL {}: {}", x.getName(), describe(x)));
                    publishing.getRepositories().forEach(x -> LOGGER.debug("++ mvg-bbb:     bbb REPO {}: {}", x.getName(), describe(x)));
                }
                LOGGER.debug("++ mvg-bbb: ---------------------------]");
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
