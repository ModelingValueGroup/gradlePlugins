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

import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

public class InfoGradle {
    private static final Path          USER_PROP_FILE  = Paths.get(System.getProperty("user.home"), ".gradle", GRADLE_PROPERTIES_FILE);
    private static final DotProperties USER_HOME_PROPS = new DotProperties(USER_PROP_FILE);

    private static volatile InfoGradle instance;

    public static synchronized void setGradle(Path absProjectDir, String projectName) {
        if (instance == null) {
            instance = new InfoGradle(absProjectDir, projectName);
        }
        if (!instance.absProjectDir.equals(absProjectDir) || !instance.projectName.equals(projectName)) {
            LOGGER.info("+ mvg: ~~~~~~ STRANGE: InfoGradle got a new Gradle object: {} -> {} and {} -> {}",
                    instance.projectName, projectName,
                    instance.absProjectDir, absProjectDir);
            instance = new InfoGradle(absProjectDir, projectName);
        }
    }

    private static synchronized InfoGradle instance() {
        if (instance == null) {
            throw new Error("InfoGradle.setGradle() should be called first");
        }
        return instance;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static DotProperties getGradleDotProperties() {
        return instance == null ? USER_HOME_PROPS : instance().gradleDotProperties;
    }

    public static Path getAbsProjectDir() {
        return instance().absProjectDir;
    }

    public static Path getWorkflowsDir() {
        return instance().workflowsDir;
    }

    public static String getBranch() {
        return instance().branch;
    }

    public static String getMvgRepoName() {
        return instance().mvgRepoName;
    }

    public static boolean isMvgCI_orTesting() {
        return (Info.CI && getMvgRepoName() != null) || Info.TESTING;
    }

    public static boolean isMasterBranch() {
        return isMasterBranch(getBranch());
    }

    public static boolean isMasterBranch(String branch) {
        return Info.MASTER_BRANCH.equals(branch);
    }

    public static boolean isDevelopBranch() {
        return getBranch().equals(Info.DEVELOP_BRANCH);
    }

    public static <T> T selectMasterDevelopElse(T master, T develop, T other) {
        return isMasterBranch() ? master : isDevelopBranch() ? develop : other;
    }

    public static Action<MavenArtifactRepository> getGithubMavenRepoMaker(boolean forMaster) {
        return forMaster ? instance().repoMakerForMaster : instance().repoMakerForOther;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private final Path                            absProjectDir;
    private final String                          projectName;
    private final Path                            workflowsDir;
    private final String                          mvgRepoName;
    private final String                          branch;
    private final Action<MavenArtifactRepository> repoMakerForMaster;
    private final Action<MavenArtifactRepository> repoMakerForOther;
    private final DotProperties                   gradleDotProperties;

    public InfoGradle(Path absProjectDir, String projectName) {
        this.absProjectDir = absProjectDir;
        this.projectName = projectName;

        gradleDotProperties = new DotProperties(USER_HOME_PROPS, absProjectDir.resolve(GRADLE_PROPERTIES_FILE));
        workflowsDir = workflowsDir();
        mvgRepoName = mvgRepoName();
        branch = branch();
        repoMakerForMaster = githubMavenRepoMaker(true);
        repoMakerForOther = githubMavenRepoMaker(false);
    }

    private Path workflowsDir() {
        return absProjectDir.resolve(".github").resolve("workflows");
    }

    private String branch() {
        Path headFile = absProjectDir.resolve(Info.GIT_HEAD_FILE).toAbsolutePath();
        if (Files.isRegularFile(headFile)) {
            try {
                List<String> lines = Files.readAllLines(headFile);
                if (!lines.isEmpty() && lines.get(0).startsWith(Info.GIT_HEAD_FILE_START)) {
                    return lines.get(0).replaceFirst("^" + Pattern.quote(Info.GIT_HEAD_FILE_START), "");
                }
            } catch (IOException e) {
                LOGGER.warn("could not read " + headFile + " to determine git-branch", e);
            }
        }
        LOGGER.warn("could not determine git branch (because {} not found), assuming branch '{}'", headFile, Info.DEFAULT_BRANCH);
        return Info.DEFAULT_BRANCH;
    }

    private String mvgRepoName() {
        Path configFile = absProjectDir.resolve(Info.GIT_CONFIG_FILE).toAbsolutePath();
        if (Files.isRegularFile(configFile)) {
            try {
                String url = Files.readAllLines(configFile)
                        .stream()
                        .filter(l -> l.matches("\\s*url\\s*=.*"))
                        .map(l -> l.replaceAll(".*=\\s*", ""))
                        .findFirst().orElse(null);
                if (url == null) {
                    LOGGER.warn("could not determine if MVG repo: could not find a url in the git config file at {}", configFile);
                } else if (!url.startsWith(Info.MVG_REPO_BASE_URL)) {
                    LOGGER.warn("could not determine if MVG repo: the repo at {} is not an MVG repo", configFile);
                } else {
                    return url.replaceFirst(Pattern.quote(Info.MVG_REPO_BASE_URL), "").replaceFirst("\\.git$", "");
                }
            } catch (IOException e) {
                LOGGER.warn("could not determine if MVG repo: could not read " + configFile, e);
            }
        } else {
            LOGGER.warn("could not determine if MVG repo: {} not found (assuming non-github and non-MVG repo)", configFile);
        }
        return null;
    }

    private Action<MavenArtifactRepository> githubMavenRepoMaker(boolean isMaster) {
        String name = isMaster ? absProjectDir.getFileName().toString() : Info.PACKAGES_SNAPSHOTS_REPO_NAME;
        String url  = Info.MVG_MAVEN_REPO_BASE_URL + name;
        return mar -> {
            mar.setUrl(url);
            mar.setName(name);
            mar.credentials(c -> {
                c.setUsername("");
                c.setPassword(Info.ALLREP_TOKEN);
            });
            LOGGER.info("+ mvg: REPOMAKER created maven repo: name={} url={} pw={}", name, url, Util.hide(Info.ALLREP_TOKEN));
        };
    }
}
