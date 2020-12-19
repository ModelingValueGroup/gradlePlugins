package org.modelingvalue.gradle.corrector;

import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultConvention;

public class TagExtension {
    public static TagExtension make(Project project, String name) {
        return ((DefaultConvention) project.getExtensions()).create(name, TagExtension.class);
    }
}
