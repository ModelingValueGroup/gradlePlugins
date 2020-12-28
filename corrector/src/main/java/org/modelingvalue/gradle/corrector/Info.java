//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public interface Info {
    String  CORRECTOR_TASK_NAME = "mvgCorrector";
    String  TAG_TASK_NAME       = "mvgTagger";
    Logger  LOGGER              = Logging.getLogger(CORRECTOR_TASK_NAME);
    boolean CI                  = Boolean.parseBoolean(envOrProp("CI", "false"));
    String  ALLREP_TOKEN        = envOrProp("ALLREP_TOKEN", "DRY");
    String  DEFAULT_BRANCH      = "refs/heads/plugh";

    static String getGithubRef(Project project) {
        Path headFile = project.getRootDir().toPath().resolve(".git/HEAD");
        try {
            String firstLine = Files.readAllLines(headFile).get(0);
            return firstLine.replaceAll("^ref: ", "");
        } catch (IOException e) {
            LOGGER.warn("could not determine git branch ({} not found), assuming branch '{}'", headFile, DEFAULT_BRANCH);
            return DEFAULT_BRANCH;
        }
    }

    static boolean isMasterBranch(Project project) {
        return getGithubRef(project).equals("refs/heads/master");
    }
}
