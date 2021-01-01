package org.modelingvalue.gradle.corrector;

import static java.util.regex.Pattern.quote;
import static org.modelingvalue.gradle.corrector.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.corrector.Info.CI;
import static org.modelingvalue.gradle.corrector.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.corrector.Info.LOGGER;
import static org.modelingvalue.gradle.corrector.Info.PREPARATION_GROUP;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class Corrector {
    private final Project            project;
    private final CorrectorExtension ext;

    public Corrector(Project project) {
        this.project = project;
        ext = CorrectorExtension.make(project, CORRECTOR_TASK_NAME);
        TaskProvider<Task> tp = project.getTasks().register(CORRECTOR_TASK_NAME, this::setup);

        // let all tasks depend on me...
        project.getTasks().stream()
                .filter(t -> !t.getName().equals(tp.getName()))                                                           // ... not myself (duh)
                .filter(t -> !t.getName().matches("(?i)" + quote(LifecycleBasePlugin.CLEAN_TASK_NAME) + ".*"))      // ... not the cleaning tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)" + quote(HelpTasksPlugin.HELP_GROUP)))              // ... not the help group tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)build setup"))                                       // ... not the build setup group tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)gradle enterprise"))                                 // ... not the gradle enterprise group tasks
                .peek(t -> LOGGER.info("+ adding task dependency: {} before {}", tp.getName(), t.getName()))
                .forEach(t -> t.dependsOn(tp));
    }

    private void setup(Task task) {
        task.setGroup(PREPARATION_GROUP);
        task.setDescription("correct various sources (version, headers, eols) and push to git");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ running corrector task on project {}, dir={}", project.getName(), project.getRootDir());
        try {
            Set<Path> changes = new HashSet<>();

            changes.addAll(new EolCorrector(ext).generate().getChangedFiles());
            changes.addAll(new HdrCorrector(ext).generate().getChangedFiles());
            changes.addAll(new VerCorrector(ext).generate().getChangedFiles());

            LOGGER.info("+ changed {} files (CI={}, have-token={})", changes.size(), CI, ALLREP_TOKEN != null);

            if (!changes.isEmpty() && CI && ALLREP_TOKEN != null) {
                GitUtil.push(ext.getRoot(), changes, GitUtil.NO_CI_MESSAGE + " updated by corrector");
            }
        } catch (IOException e) {
            throw new Error("could not correct files", e);
        }
    }
}
