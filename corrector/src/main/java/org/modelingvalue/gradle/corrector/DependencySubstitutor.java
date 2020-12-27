package org.modelingvalue.gradle.corrector;

import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.logging.Logger;

public class DepSubst {
    public static final Logger LOGGER              = Info.LOGGER;
    public static final String BRANCH_INDICATOR    = "-BRANCH";
    public static final String SNAPSHOT_POST       = "-+";//"-SNAPSHOT";
    public static final String SNAPSHOTS_GROUP_PRE = "snapshots.";
    public static final String REASON              = "making use of Branch Based Building";

    private final Project project;
    private final boolean isCi;
    private final boolean isMasterBranch;
    private final String  bbbVersion;

    public DepSubst(Project project) {
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

    private void checkReplacement(DependencySubstitution depSub, ModuleComponentSelector component) {
        if (component.getVersion().endsWith(BRANCH_INDICATOR)) {
            String replacement = makeBbbGroup(component.getGroup()) + ":" + makeBbbArtifact(component.getModule()) + ":" + makeBbbVersion(component.getVersion());
            depSub.useTarget(replacement, REASON);
            LOGGER.info("+ replaced dependency [" + component + " => " + replacement + "]");
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
