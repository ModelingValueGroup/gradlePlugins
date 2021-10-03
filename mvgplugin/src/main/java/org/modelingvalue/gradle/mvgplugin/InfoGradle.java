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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;

public class InfoGradle {
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static Path getProjectDir(Gradle gradle) {
        return instance(gradle).projectDir;
    }

    public static Path getWorkflowsDir(Gradle gradle) {
        return instance(gradle).workflowsDir;
    }

    public static String getBranch(Gradle gradle) {
        return instance(gradle).branch;
    }

    public static String getProjectName(Gradle gradle) {
        return instance(gradle).projectName;
    }

    public static boolean isMvgRepo(Gradle gradle) {
        return instance(gradle).repoName != null;
    }

    public static String getRepoName(Gradle gradle) {
        return instance(gradle).repoName;
    }

    public static boolean isMasterBranch(Gradle gradle) {
        return getBranch(gradle).equals(Info.MASTER_BRANCH);
    }

    public static boolean isDevelopBranch(Gradle gradle) {
        return getBranch(gradle).equals(Info.DEVELOP_BRANCH);
    }

    public static <T> T selectMasterDevelopElse(Gradle gradle, T master, T develop, T other) {
        return isMasterBranch(gradle) ? master : isDevelopBranch(gradle) ? develop : other;
    }

    public static Action<MavenArtifactRepository> getGithubMavenRepoMaker(Gradle gradle, boolean isMaster) {
        return isMasterBranch(gradle) ? instance(gradle).repoMakerForMaster : instance(gradle).repoMakerForOther;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static volatile InfoGradle instance;

    private static InfoGradle instance(Gradle gradle) {
        if (instance == null) {
            synchronized (InfoGradle.class) {
                if (instance == null) {
                    instance = new InfoGradle(gradle);
                }
            }
        }
        return instance;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private final Path                            projectDir;
    private final Path                            workflowsDir;
    private final String                          projectName;
    private final String                          repoName;
    private final String                          branch;
    private final Action<MavenArtifactRepository> repoMakerForMaster;
    private final Action<MavenArtifactRepository> repoMakerForOther;

    public InfoGradle(Gradle gradle) {
        projectDir = projectDir(gradle);
        workflowsDir = workflowsDir();
        projectName = gradle.getRootProject().getName();
        repoName = getRepoName();
        branch = getBranch();
        repoMakerForMaster = getGithubMavenRepoMaker(true);
        repoMakerForOther = getGithubMavenRepoMaker(false);
    }

    private Path projectDir(Gradle gradle) {
        return gradle.getRootProject().getRootDir().toPath().toAbsolutePath();
    }

    private Path workflowsDir() {
        return projectDir.resolve(".github").resolve("workflows");
    }

    private String getBranch() {
        Path headFile = projectDir.resolve(Info.GIT_HEAD_FILE).toAbsolutePath();
        if (Files.isRegularFile(headFile)) {
            try {
                List<String> lines = Files.readAllLines(headFile);
                if (!lines.isEmpty() && lines.get(0).startsWith(Info.GIT_HEAD_FILE_START)) {
                    return lines.get(0).replaceFirst("^" + Pattern.quote(Info.GIT_HEAD_FILE_START), "");
                }
            } catch (IOException e) {
                Info.LOGGER.warn("could not read " + headFile + " to determine git-branch", e);
            }
        }
        Info.LOGGER.warn("could not determine git branch (because {} not found), assuming branch '{}'", headFile, Info.DEFAULT_BRANCH);
        return Info.DEFAULT_BRANCH;
    }

    private String getRepoName() {
        Path configFile = projectDir.resolve(Info.GIT_CONFIG_FILE).toAbsolutePath();
        if (Files.isRegularFile(configFile)) {
            try {
                String url = Files.readAllLines(configFile)
                        .stream()
                        .filter(l -> l.matches("\\s*url\\s*=.*"))
                        .map(l -> l.replaceAll(".*=\\s*", ""))
                        .findFirst().orElse(null);
                if (url != null && url.startsWith(Info.MVG_REPO_BASE_URL)) {
                    return url.replaceFirst(Pattern.quote(Info.MVG_REPO_BASE_URL), "").replaceFirst("\\.git$", "");
                }
            } catch (IOException e) {
                Info.LOGGER.warn("could not read " + configFile + " to determine git-branch", e);
            }
        }
        Info.LOGGER.warn("could not determine github repo (because {} not found), assuming non-github and non-MVG repo", configFile);
        return null;
    }

    private Action<MavenArtifactRepository> getGithubMavenRepoMaker(boolean isMaster) {
        String name = isMaster ? projectDir.getFileName().toString() : Info.PACKAGES_SNAPSHOTS_REPO_NAME;
        String url  = Info.MVG_MAVEN_REPO_BASE_URL + name;
        return mar -> {
            mar.setUrl(url);
            mar.setName(name);
            mar.credentials(c -> {
                c.setUsername("");
                c.setPassword(Info.ALLREP_TOKEN);
            });
            Info.LOGGER.info("+ REPOMAKER created maven repo: name={} url={} pw={}", name, url, Util.hide(Info.ALLREP_TOKEN));
        };
    }
}
