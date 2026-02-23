//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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
import static org.modelingvalue.gradle.mvgplugin.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MODELING_VALUE_GROUP;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMvgCI_orTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class MvgCorrector {
    private static final List<Pattern> NOT_BEFORE_PATTERNS = Stream.of(
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
    ).map(s -> Pattern.compile("^(?i)" + s + "$")).toList();

    private final MvgCorrectorExtension ext;

    public MvgCorrector(Gradle gradle) {
        ext = MvgCorrectorExtension.make(gradle);
        TaskProvider<Task> tp = gradle.getRootProject().getTasks().register(CORRECTOR_TASK_NAME, this::setup);

        // let all tasks depend on me...
        gradle.allprojects(p -> p.getTasks().configureEach(t -> {
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
        return NOT_BEFORE_PATTERNS.stream().noneMatch(pat -> pat.matcher(name).matches());
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

            if (doCorrector(ext.getForceDependabotCorrection().get(), "Dependabot file")) {
                changes.addAll(new DependabotCorrector(ext).generate().getChangedFiles());
            }
            if (doCorrector(ext.getForceBashCorrection().get(), "with bash scripts")) {
                changes.addAll(new BashCorrector(ext).generate().getChangedFiles());
            }
            if (doCorrector(ext.getForceEolCorrection().get(), "EOLs")) {
                changes.addAll(new EolCorrector(ext).generate().getChangedFiles());
            }
            if (doCorrector(ext.getForceHeaderCorrection().get(), "headers")) {
                changes.addAll(new HeaderCorrector(ext).generate().getChangedFiles());
            }
            if (doCorrector(ext.getForceVersionCorrection().get(), "version")) {
                changes.addAll(new VersionCorrector(ext).generate().getChangedFiles());
            }

            LOGGER.info("+ mvg: changed {} files", changes.size());

            if (!changes.isEmpty() && isMvgCI_orTesting() && ALLREP_TOKEN != null) {
                GitUtil.stageCommitPush(ext.getRoot(), GitUtil.NO_CI_COMMIT_MARKER + " updated by mvgplugin", changes);
            }
        } catch (IOException e) {
            throw new GradleException("could not correct files", e);
        }
    }

    private boolean doCorrector(boolean force, String name) {
        boolean b = Info.CI || force;
        if (!b) {
            LOGGER.info("+ mvg: NOT correcting {} (CI={}, force={})", name, Info.CI, force);
        }
        return b;
    }
}
