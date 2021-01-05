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

import static java.util.regex.Pattern.quote;
import static org.modelingvalue.gradle.corrector.Info.LOGGER;
import static org.modelingvalue.gradle.corrector.Info.TAG_TASK_NAME;
import static org.modelingvalue.gradle.corrector.Info.WRAP_UP_GROUP;

import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskProvider;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
class Tagger {
    private final Gradle          gradle;
    private final TaggerExtension ext;

    public Tagger(Gradle gradle) {
        this.gradle = gradle;
        ext = TaggerExtension.make(gradle.getRootProject(), TAG_TASK_NAME);
        TaskProvider<Task> tp = gradle.getRootProject().getTasks().register(TAG_TASK_NAME, this::setup);

        // let me depend on all publish tasks...
        gradle.allprojects(p -> p.getTasks().all(t -> {
            LOGGER.trace("+ checking if task '{}' should be before '{}'", tp.getName(), t.getName());
            if (t.getName().matches(quote(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME) + ".*")) {
                LOGGER.info("+ adding task dependency: {} after {}", tp.getName(), t.getName());
                t.finalizedBy(tp);
            }
        }));
    }

    private void setup(Task task) {
        task.setGroup(WRAP_UP_GROUP);
        task.setDescription("tag the git repo with the current version");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ execute {} task", TAG_TASK_NAME);
        String tag = "v" + gradle.getRootProject().getVersion();
        if (Info.isMasterBranch(gradle)) {
            LOGGER.info("+ tagging this version with '{}' because this is the master branch", tag);
            GitUtil.tag(gradle.getRootProject().getRootDir().toPath(), tag);
        } else {
            LOGGER.info("+ not tagging this version with '{}' because this is not the master branch", tag);
        }
    }
}
