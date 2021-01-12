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

import static org.modelingvalue.gradle.corrector.Util.envOrProp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public interface Info {
    String                          CORRECTOR_TASK_NAME            = "mvgCorrector";
    String                          TAG_TASK_NAME                  = "mvgTagger";
    Logger                          LOGGER                         = Logging.getLogger(CORRECTOR_TASK_NAME);
    boolean                         CI                             = Boolean.parseBoolean(envOrProp("CI", "false"));
    String                          ALLREP_TOKEN                   = envOrProp("ALLREP_TOKEN", "DRY");
    String                          DEFAULT_BRANCH                 = "refs/heads/develop";
    String                          PREPARATION_GROUP              = "preparation";
    String                          WRAP_UP_GROUP                  = "wrap-up";
    String                          MVG_MAVEN_REPO_URL             = "https://maven.pkg.github.com/ModelingValueGroup/packages";
    String                          MVG_MAVEN_REPO_SNAPSHOTS_URL   = "https://maven.pkg.github.com/ModelingValueGroup/packages-snapshots";
    Action<MavenArtifactRepository> MVG_MAVEN_REPO_MAKER           = getRepoMaker("MvgMaven", MVG_MAVEN_REPO_URL);
    Action<MavenArtifactRepository> MVG_MAVEN_SNAPSHOTS_REPO_MAKER = getRepoMaker("MvgMavenSnapshots", MVG_MAVEN_REPO_SNAPSHOTS_URL);
    String                          PLUGIN_META_URL                = "https://plugins.gradle.org/m2/gradle/plugin/org/modelingvalue/corrector/maven-metadata.xml";

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

    static String getGithubRef(Gradle gradle) {
        Path headFile = gradle.getRootProject().getRootDir().toPath().resolve(".git/HEAD").toAbsolutePath();
        if (Files.isRegularFile(headFile)) {
            try {
                String firstLine = Files.readAllLines(headFile).get(0);
                return firstLine.replaceAll("^ref: ", "");
            } catch (IOException e) {
                LOGGER.warn("could not read " + headFile + " to determine git-branch", e);
            }
        }
        LOGGER.warn("could not determine git branch (because {} not found), assuming branch '{}'", headFile, DEFAULT_BRANCH);
        return DEFAULT_BRANCH;
    }

    static boolean isMasterBranch(Gradle gradle) {
        return getGithubRef(gradle).equals("refs/heads/master");
    }
}
