package org.modelingvalue.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * A simple 'hello world' plugin.
 */
public class GradlePluginsPlugin implements Plugin<Project> {
    public void apply(Project project) {
        GreetingPluginExtension extension = project.getExtensions().create("greeting", GreetingPluginExtension.class);

        System.err.println(" project = " + project);
        project.artifacts(x -> System.err.println(" A - " + x));
        project.getConfigurations().forEach(x -> System.err.println(" C - " + x));
        project.getAllprojects().forEach(x -> System.err.println(" P - " + x));

        project.getTasks().register("greeting", task -> {
            task.doLast(s -> System.out.println("Hello from plugin 'org.modelingvalue.gradle.greeting' q=" + extension.getQ()));
        });
    }
}
