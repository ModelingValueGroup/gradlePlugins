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

import static java.util.regex.Pattern.quote;
import static org.modelingvalue.gradle.mvgplugin.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.CI;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_CI;
import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_GROUP;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class MvgCorrector {
    private final Gradle                gradle;
    private final MvgCorrectorExtension ext;

    public MvgCorrector(Gradle gradle) {
        this.gradle = gradle;
        ext = MvgCorrectorExtension.make(gradle);
        TaskProvider<Task> tp = gradle.getRootProject().getTasks().register(CORRECTOR_TASK_NAME, this::setup);

        // let all tasks depend on me...
        gradle.allprojects(p -> p.getTasks().all(t -> {
            LOGGER.debug("+ checking if task '{}' should be before '{}'", tp.getName(), t.getName());
            if (!t.getName().equals(tp.getName())                                                               // ... not myself (duh)
                    && !t.getName().matches("(?i)" + quote(LifecycleBasePlugin.CLEAN_TASK_NAME) + ".*")   // ... not the cleaning tasks
                    && !("" + t.getGroup()).matches("(?i)" + quote(HelpTasksPlugin.HELP_GROUP))           // ... not the help group tasks
                    && !("" + t.getGroup()).matches("(?i)build setup")                                    // ... not the build setup group tasks
                    && !("" + t.getGroup()).matches("(?i)gradle enterprise")                              // ... not the gradle enterprise group tasks
            ) {
                LOGGER.info("+ adding task dependency: {} before {}", tp.getName(), t.getName());
                t.dependsOn(tp);
            }
        }));
    }

    private void setup(Task task) {
        task.setGroup(MVG_GROUP);
        task.setDescription("correct various sources (version, headers, eols) and push to git");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ execute {} task", CORRECTOR_TASK_NAME);
        try {
            Set<Path> changes = new HashSet<>();

            changes.addAll(new EolCorrector(ext).generate().getChangedFiles());
            changes.addAll(new HeaderCorrector(ext).generate().getChangedFiles());
            changes.addAll(new VersionCorrector(ext).generate().getChangedFiles());

            LOGGER.info("+ changed {} files ({}={}, master={}, {}={})", changes.size(), PROP_NAME_CI, CI, Info.isMasterBranch(gradle), PROP_NAME_ALLREP_TOKEN, ALLREP_TOKEN != null);

            if (!changes.isEmpty() && CI && ALLREP_TOKEN != null) {
                GitUtil.push(ext.getRoot(), changes, GitUtil.NO_CI_MESSAGE + " updated by mvgplugin");
            }
        } catch (IOException e) {
            throw new Error("could not correct files", e);
        }
    }
}
