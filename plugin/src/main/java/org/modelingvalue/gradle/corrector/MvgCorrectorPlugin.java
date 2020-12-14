package org.modelingvalue.gradle.corrector;

import static org.modelingvalue.gradle.corrector.Info.EXTENSION_NAME;

import java.io.IOException;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultConvention;

@SuppressWarnings("unused")
public class MvgCorrectorPlugin implements Plugin<Project> {
    public void apply(Project project) {
        MvgCorrectorPluginExtension extension = ((DefaultConvention) project.getExtensions()).create(EXTENSION_NAME, MvgCorrectorPluginExtension.class, project);

        project.getTasks().register(EXTENSION_NAME, task -> task.doLast(s -> {
            try {
                new HeaderCorrector(extension).generate();
                new EolCorrector(extension).generate();
            } catch (IOException e) {
                throw new Error("could not correct file", e);
            }
        }));
    }
}
