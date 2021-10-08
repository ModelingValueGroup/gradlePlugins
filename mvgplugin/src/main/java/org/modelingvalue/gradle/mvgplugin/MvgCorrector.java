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
import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MODELING_VALUE_GROUP;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isTestingOrMvgCI;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class MvgCorrector {
    private final MvgCorrectorExtension ext;

    public MvgCorrector(Gradle gradle) {
        ext = MvgCorrectorExtension.make(gradle);
        TaskProvider<Task> tp = gradle.getRootProject().getTasks().register(CORRECTOR_TASK_NAME, this::setup);

        // let all tasks depend on me...
        gradle.allprojects(p -> p.getTasks().all(t -> {
            String name  = t.getName();
            String group = "" + t.getGroup(); // may return null
            LOGGER.debug("++ mvg: checking if task '{}' should be before '{}' (group '{}'", tp.getName(), name, group);
            if (!name.equals(tp.getName())                                                  // ... not myself (duh)
                    && isANotBeforeTask(name)                                               // ... not a task that needs to be excluded
                    && !group.matches("(?i)" + quote(HelpTasksPlugin.HELP_GROUP))     // ... not the help group tasks
                    && !group.matches("(?i)build setup")                              // ... not the build setup group tasks
                    && !group.matches("(?i)gradle enterprise")                        // ... not the gradle enterprise group tasks
            ) {
                LOGGER.info("+ mvg: adding task dependency: {} before {} (group {})", tp.getName(), name, t.getGroup());
                t.dependsOn(tp);
            }
        }));
    }

    private boolean isANotBeforeTask(String name) {
        return Stream.of(
                ".*jar",
                ".*kotlin.*",
                "buildEnvironment",
                "buildScanPublishPrevious",
                "components",
                "dependen.*",
                "help",
                "init",
                "model",
                "mvg.*",
                "outgoingVariants",
                "prepareKotlinBuildScriptModel",
                "process.*",
                "projects",
                "properties",
                "provisionGradleEnterpriseAccessKey",
                "publish.*",
                "tasks",
                "test",
                "wrapper",
                quote(LifecycleBasePlugin.CLEAN_TASK_NAME) + ".*"
        ).map(s -> Pattern.compile("^(?i)" + s + "$")).collect(Collectors.toList()).stream().noneMatch(pat -> pat.matcher(name).matches());
    }

    private void setup(Task task) {
        task.setGroup(MODELING_VALUE_GROUP);
        task.setDescription("correct various sources (version, headers, eols) and push to git");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ mvg: execute {} task", CORRECTOR_TASK_NAME);
        try {
            Set<Path> changes = new HashSet<>();

            changes.addAll(new EolCorrector(ext).generate().getChangedFiles());
            changes.addAll(new HeaderCorrector(ext).generate().getChangedFiles());
            changes.addAll(new VersionCorrector(ext).generate().getChangedFiles());
            changes.addAll(new DependabotCorrector(ext).generate().getChangedFiles());
            changes.addAll(new BashCorrector(ext).generate().getChangedFiles());

            LOGGER.info("+ mvg: changed {} files", changes.size());

            if (!changes.isEmpty() && isTestingOrMvgCI() && ALLREP_TOKEN != null) {
                GitUtil.stageCommitPush(ext.getRoot(), GitUtil.NO_CI_COMMIT_MARKER + " updated by mvgplugin", changes);
            }
        } catch (IOException e) {
            throw new Error("could not correct files", e);
        }
    }
}
