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
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class CorrectorTask {
    private final CorrectorExtension ext1;

    public CorrectorTask(Project project) {
        ext1 = CorrectorExtension.make(project, CORRECTOR_TASK_NAME);
        TaskProvider<Task> correctTask = project.getTasks().register(CORRECTOR_TASK_NAME, task -> setupCorrectorTask(project, task));
        // let all tasks depend on me...
        project.getTasks().stream()
                .filter(t -> !t.getName().equals(correctTask.getName()))                                                  // ... not myself (duh)
                .filter(t -> !t.getName().matches("(?i)" + quote(LifecycleBasePlugin.CLEAN_TASK_NAME) + ".*"))      // ... not the cleaning tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)" + quote(HelpTasksPlugin.HELP_GROUP)))              // ... not the help group tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)build setup"))                                       // ... not the build setup group tasks
                .filter(t -> !("" + t.getGroup()).matches("(?i)gradle enterprise"))                                 // ... not the gradle enterprise group tasks
                .peek(t -> LOGGER.info("+ adding task dependency: {} before {}", correctTask.getName(), t.getName()))
                .forEach(t -> t.dependsOn(correctTask));
    }

    private void setupCorrectorTask(Project project, Task task) {
        task.setGroup(PREPARATION_GROUP);
        task.setDescription("correct various sources (version, headers, eols) and push to git");
        task.doLast(s -> executeCorrectorTask(project));
        project.getExtensions().getExtensionsSchema().forEach(es -> System.out.printf("TOMTOMTOM ESX   %-20s %s\n", es.getName(), es.getPublicType()));
    }

    private void executeCorrectorTask(Project project) {
        PublishingExtension publishing = (PublishingExtension) project.getExtensions().findByName("publishing");
        if (publishing != null) {
            publishing.getRepositories().forEach(rep -> System.out.println("TOMTOMTOM    rep=" + rep.getName()));
        }

        LOGGER.info("+ running corrector task on project {}, dir={}", project.getName(), project.getRootDir());
        try {

            Set<Path> changes = new HashSet<>();

            changes.addAll(new EolCorrector(ext1).generate().getChangedFiles());
            changes.addAll(new HdrCorrector(ext1).generate().getChangedFiles());
            changes.addAll(new VerCorrector(ext1).generate().getChangedFiles());

            LOGGER.info("+ changed {} files (CI={}, have-token={})", changes.size(), CI, ALLREP_TOKEN != null);

            if (!changes.isEmpty() && CI && ALLREP_TOKEN != null) {
                GitUtil.push(ext1.getRoot(), changes, GitUtil.NO_CI_MESSAGE + " updated by corrector");
            }
        } catch (IOException e) {
            throw new Error("could not correct files", e);
        }
    }
}
