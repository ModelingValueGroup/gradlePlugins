//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.corrector;

import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;

public class DependencySubstitutor {
    public static final Logger LOGGER              = Info.LOGGER;
    public static final String BRANCH_INDICATOR    = "-BRANCH";
    public static final String SNAPSHOT_POST       = "-SNAPSHOT";
    public static final String SNAPSHOTS_GROUP_PRE = "snapshots.";
    public static final String REASON              = "making use of Branch Based Building";

    public static String replaceOrNull(Gradle gradle, String gav) {
        return new DependencySubstitutor(gradle).substitute(gav);
    }

    public static String replace(Gradle gradle, String gav) {
        String gavNew = replaceOrNull(gradle, gav);
        return gavNew == null ? gav : gavNew;
    }

    private final Gradle  gradle;
    private final boolean isCi;
    private final boolean isMasterBranch;
    private final String  bbbVersion;

    public DependencySubstitutor(Project project) {
        this(project.getGradle());
        attach();
    }

    public DependencySubstitutor(Gradle gradle) {
        this.gradle = gradle;
        isCi = Info.CI;
        isMasterBranch = Info.isMasterBranch(gradle);
        bbbVersion = String.format("%08x", Info.getGithubRef(gradle).hashCode()) + SNAPSHOT_POST;
    }

    public void attach() {
        gradle.allprojects(project ->
                project.getConfigurations().all(conf ->
                        conf.resolutionStrategy(resolutionStrategy ->
                                resolutionStrategy.getDependencySubstitution().all(depSub -> {
                                    ComponentSelector component = depSub.getRequested();
                                    if (component instanceof ModuleComponentSelector) {
                                        checkReplacement(depSub, (ModuleComponentSelector) component);
                                    }
                                }))));
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
