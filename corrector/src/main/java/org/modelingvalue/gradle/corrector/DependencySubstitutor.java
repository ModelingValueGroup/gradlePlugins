package org.modelingvalue.gradle.corrector;

import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.logging.Logger;

public class DependencySubstitutor {
    public static final Logger LOGGER              = Info.LOGGER;
    public static final String BRANCH_INDICATOR    = "-BRANCH";
    public static final String SNAPSHOT_POST       = "-SNAPSHOT";
    public static final String SNAPSHOTS_GROUP_PRE = "snapshots.";
    public static final String REASON              = "making use of Branch Based Building";

    public static String replaceOrNull(Project project, String gav) {
        return new DependencySubstitutor(project).substitute(gav);
    }

    public static String replace(Project project, String gav) {
        String gavNew = replaceOrNull(project, gav);
        return gavNew == null ? gav : gavNew;
    }

    private final Project project;
    private final boolean isCi;
    private final boolean isMasterBranch;
    private final String  bbbVersion;

    public DependencySubstitutor(Project project) {
        this.project = project;
        isCi = Info.CI;
        isMasterBranch = Info.isMasterBranch(project);
        bbbVersion = String.format("%08x", Info.getGithubRef(project).hashCode()) + SNAPSHOT_POST;
    }

    public void attach() {
        project.getConfigurations().all(conf ->
                conf.resolutionStrategy(resolutionStrategy ->
                        resolutionStrategy.getDependencySubstitution().all(depSub -> {
                            ComponentSelector component = depSub.getRequested();
                            if (component instanceof ModuleComponentSelector) {
                                checkReplacement(depSub, (ModuleComponentSelector) component);
                            }
                        })));
    }

    public String substitute(String gav) {
        String[] parts = gav.split(":");
        return parts.length != 3 ? null : substitute(parts[0], parts[1], parts[2]);
    }

    public String substitute(String g, String a, String v) {
        return !v.endsWith(BRANCH_INDICATOR) ? null : makeBbbGroup(g) + ":" + makeBbbArtifact(a) + ":" + makeBbbVersion(v);
    }

    private void checkReplacement(DependencySubstitution depSub, ModuleComponentSelector component) {
        String replacement = substitute(component.getGroup(), component.getModule(), component.getVersion());
        if (replacement != null) {
            depSub.useTarget(replacement, REASON);
            LOGGER.info("+ dependency     replaced: " + component + " => " + replacement);
        } else {
            LOGGER.info("+ dependency NOT replaced: " + component);
        }
    }

    private String makeBbbGroup(String g) {
        return isCi && isMasterBranch ? g : SNAPSHOTS_GROUP_PRE + g;
    }

    private String makeBbbArtifact(String a) {
        return a;
    }

    private String makeBbbVersion(String v) {
        return isCi && isMasterBranch ? v : bbbVersion;
    }
}
