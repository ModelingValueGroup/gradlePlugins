package org.modelingvalue.gradle;


import org.gradle.api.Project;
import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

public class GradlePluginsPluginTest {
    @Test
    public void pluginRegistersATask() {
        // Create a test project and apply the plugin
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.modelingvalue.gradle.greeting");

        // Verify the result
        Assert.assertNotNull(project.getTasks().findByName("greeting"));
    }
}
