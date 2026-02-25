//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import static java.util.regex.Pattern.quote;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MODELING_VALUE_GROUP;
import static org.modelingvalue.gradle.mvgplugin.Info.TAG_TASK_NAME;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskProvider;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
class MvgTagger {
    private final Gradle gradle;

    public MvgTagger(Gradle gradle) {
        this.gradle = gradle;
        TaskProvider<Task> tp = gradle.getRootProject().getTasks().register(TAG_TASK_NAME, this::setup);

        // let me depend on all publish tasks...
        gradle.allprojects(p -> p.getTasks().configureEach(t -> {
            LOGGER.debug("++ mvg: checking if task '{}' should be before '{}'", tp.getName(), t.getName());
            if (t.getName().matches(quote(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME) + ".*")) {
                LOGGER.info("+ mvg: adding task dependency: {} after {}", tp.getName(), t.getName());
                t.finalizedBy(tp);
            }
        }));
    }

    private void setup(Task task) {
        task.setGroup(MODELING_VALUE_GROUP);
        task.setDescription("tag the git repo with the current version");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ mvg: execute {} task", TAG_TASK_NAME);
        String tag = "v" + gradle.getRootProject().getVersion();
        if (tag.equals("v") || tag.equals("vnull") || tag.equals("vunspecified")) {
            LOGGER.error("mvgplugin: can not tag git with version: version of the rootProject is not set: {}", tag);
            throw new GradleException("version of the rootProject is not set");
        } else if (InfoGradle.isMasterBranch()) {
            LOGGER.info("+ mvg: tagging this version with '{}' because this is the master branch", tag);
            GitUtil.tag(InfoGradle.getAbsProjectDir(), tag);
        } else {
            LOGGER.info("+ mvg: not tagging this version with '{}' because this is not the master branch (branch={})", tag, InfoGradle.getBranch());
        }
    }
}
