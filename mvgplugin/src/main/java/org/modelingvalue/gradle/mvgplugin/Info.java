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

import static org.modelingvalue.gradle.mvgplugin.Util.envOrProp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public interface Info {
    String                          MVG_GROUP                      = "MVG";
    String                          PLUGIN_PACKAGE_NAME            = MvgPlugin.class.getPackageName();
    String                          PLUGIN_CLASS_NAME              = MvgPlugin.class.getName();
    String                          PLUGIN_NAME                    = MvgPlugin.class.getSimpleName().toLowerCase(Locale.ROOT);
    String                          CORRECTOR_TASK_NAME            = MvgCorrector.class.getSimpleName().toLowerCase(Locale.ROOT);
    String                          UPLOADER_TASK_NAME             = MvgUploader.class.getSimpleName().toLowerCase(Locale.ROOT);
    String                          TAG_TASK_NAME                  = MvgTagger.class.getSimpleName().toLowerCase(Locale.ROOT);
    String                          MPS_TASK_NAME                  = MvgMps.class.getSimpleName().toLowerCase(Locale.ROOT);
    //
    String                          GRADLE_PROPERTIES_FILE         = "gradle.properties";
    String                          PROP_NAME_VERSION              = "version";
    String                          PROP_NAME_GROUP                = "group";
    String                          PROP_NAME_VERSION_JAVA         = "version_java";
    String                          PROP_NAME_VERSION_MPS          = "version_mps";
    String                          PROP_NAME_ALLREP_TOKEN         = "ALLREP_TOKEN";
    String                          PROP_NAME_JETBRAINS_TOKEN      = "JETBRAINS_PUBLISH_TOKEN";
    String                          PROP_NAME_CI                   = "CI";
    //
    boolean                         CI                             = Boolean.parseBoolean(envOrProp(PROP_NAME_CI, "false"));
    String                          ALLREP_TOKEN                   = envOrProp(PROP_NAME_ALLREP_TOKEN, "notset");
    String                          JETBRAINS_TOKEN                = envOrProp(PROP_NAME_JETBRAINS_TOKEN, "notset");
    String                          MASTER_BRANCH                  = "master";
    String                          DEVELOP_BRANCH                 = "develop";
    String                          DEFAULT_BRANCH                 = "can-not-determine-branch";
    String                          GIT_HEAD_FILE                  = ".git/HEAD";
    String                          GIT_HEAD_FILE_START            = "ref: refs/heads/";
    String                          NO_CI_GUARD                    = "!contains(github.event.head_commit.message, '[no-ci]')";
    String                          MIN_TEST_HEAP_SIZE             = "2g";
    String                          JUNIT_VERSION                  = "5.7.2";
    String                          JUNIT_GROUP_ID                 = "org.junit.jupiter";
    List<String>                    JUNIT_IMPLEMENTATION_DEPS      = List.of(JUNIT_GROUP_ID + ":junit-jupiter-api:" + Info.JUNIT_VERSION);
    List<String>                    JUNIT_RUNTIMEONLY_DEPS         = List.of(JUNIT_GROUP_ID + ":junit-jupiter-engine:" + Info.JUNIT_VERSION);
    //
    String                          JETBRAINS_UPLOAD_URL           = "https://plugins.jetbrains.com/plugin/uploadPlugin";
    String                          MVG_MAVEN_REPO_URL             = "https://maven.pkg.github.com/ModelingValueGroup/packages";
    String                          MVG_MAVEN_REPO_SNAPSHOTS_URL   = "https://maven.pkg.github.com/ModelingValueGroup/packages-snapshots";
    String                          PLUGIN_META_URL                = "https://plugins.gradle.org/m2/gradle/plugin/" + MvgPlugin.class.getPackageName().replace('.', '/') + "/maven-metadata.xml";
    //
    Action<MavenArtifactRepository> MVG_MAVEN_REPO_MAKER           = getRepoMaker("MvgMaven", MVG_MAVEN_REPO_URL);
    Action<MavenArtifactRepository> MVG_MAVEN_SNAPSHOTS_REPO_MAKER = getRepoMaker("MvgMavenSnapshots", MVG_MAVEN_REPO_SNAPSHOTS_URL);
    //
    Logger                          LOGGER                         = Logging.getLogger(PLUGIN_NAME);
    String                          NOW_STAMP                      = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmm"));

    static Action<MavenArtifactRepository> getRepoMaker(String name, String url) {
        return mar -> {
            LOGGER.info("+ REPOMAKER: name={} url={} pw={}", name, url, ALLREP_TOKEN);
            mar.setUrl(url);
            mar.setName(name);
            mar.credentials(c -> {
                c.setUsername("");
                c.setPassword(ALLREP_TOKEN);
            });
        };
    }

    static boolean isMasterBranch(Gradle gradle) {
        return getBranch(gradle).equals(MASTER_BRANCH);
    }

    static boolean isDevelopBranch(Gradle gradle) {
        return getBranch(gradle).equals(DEVELOP_BRANCH);
    }

    static <T> T selectMasterDevelopElse(Gradle gradle, T master, T develop, T other) {
        return isMasterBranch(gradle) ? master : isDevelopBranch(gradle) ? develop : other;
    }

    static String getBranch(Gradle gradle) {
        Path headFile = gradle.getRootProject().getRootDir().toPath().resolve(GIT_HEAD_FILE).toAbsolutePath();
        if (Files.isRegularFile(headFile)) {
            try {
                List<String> lines = Files.readAllLines(headFile);
                if (!lines.isEmpty() && lines.get(0).startsWith(GIT_HEAD_FILE_START)) {
                    return lines.get(0).replaceFirst("^" + Pattern.quote(GIT_HEAD_FILE_START), "");
                }
            } catch (IOException e) {
                LOGGER.warn("could not read " + headFile + " to determine git-branch", e);
            }
        }
        LOGGER.warn("could not determine git branch (because {} not found), assuming branch '{}'", headFile, DEFAULT_BRANCH);
        return DEFAULT_BRANCH;
    }
}
