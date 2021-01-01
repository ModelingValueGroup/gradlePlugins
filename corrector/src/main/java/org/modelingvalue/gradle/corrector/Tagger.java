package org.modelingvalue.gradle.corrector;

import static java.util.regex.Pattern.quote;
import static org.modelingvalue.gradle.corrector.Info.LOGGER;
import static org.modelingvalue.gradle.corrector.Info.TAG_TASK_NAME;
import static org.modelingvalue.gradle.corrector.Info.WRAP_UP_GROUP;

import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskProvider;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
class TaggerTask {
    private final TagExtension ext2;

    public TaggerTask(Project project) {
        ext2 = TagExtension.make(project, TAG_TASK_NAME);
        TaskProvider<Task> tagTask = project.getTasks().register(TAG_TASK_NAME, task -> setupTagTask(project, task));
        // let me depend on all publish tasks...
        project.getTasks().stream()
                .filter(t -> t.getName().matches(quote(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME) + ".*"))
                .peek(t -> LOGGER.info("+ adding task dependency: {} after {}", tagTask.getName(), t.getName()))
                .forEach(t -> t.finalizedBy(tagTask));
    }

    private void setupTagTask(Project project, Task task) {
        task.setGroup(WRAP_UP_GROUP);
        task.setDescription("tag the git repo with the current version");
        task.doLast(s -> executeTagTask(project));
    }

    private void executeTagTask(Project project) {
        LOGGER.info("+ running tag task on project {}, dir={}", project.getName(), project.getRootDir());
        Path   root   = project.getRootDir().toPath();
        String branch = GitUtil.getBranch(root);
        String tag    = "v" + project.getVersion();
        if (branch.equals("master")) {
            GitUtil.tag(root, tag);
        } else {
            LOGGER.info("+ not tagging this version with '{}' because this is branch '{}' which is not master", tag, branch);
        }
    }
}
