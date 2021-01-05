//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.LOGGER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MvgCorrectorPlugin implements Plugin<Project> {
    public static final String NO_CI_GUARD = "!contains(github.event.head_commit.message, '[no-ci]')";

    private Corrector          corrector;
    private Tagger             tagger;
    private BranchBasedBuilder branchBasedBuilder;

    public void apply(Project project) {
        Gradle gradle = project.getGradle();
        Project rootProject = gradle.getRootProject();

        LOGGER.info("apply {} on project {}", this.getClass().getSimpleName(), project.getName());
        if (rootProject !=project){
            LOGGER.error("the plugin {} can only be applied to the root project ({})", this.getClass().getSimpleName(), rootProject);
            throw new GradleException("the plugin " + this.getClass().getSimpleName() + " can onlly be applied to the root project");
        }

        //TOMTOMTOM
        //        DefaultConvention ext = (DefaultConvention) project.getExtensions();
        //        ext.getAsMap().forEach((k, v) -> System.out.printf("@@@@@ %-20s : %s\n", k, v));

        checkWorkflowFiles(project.getRootProject().getRootDir().toPath());

        corrector = new Corrector(gradle);
        tagger = new Tagger(gradle);
        branchBasedBuilder = new BranchBasedBuilder(gradle);
    }

    private static void checkWorkflowFiles(Path root) {
        Path workflowsDir = root.resolve(".github").resolve("workflows");
        if (!Files.isDirectory(workflowsDir)) {
            LOGGER.warn("RECURSION DANGER: workflows dir not found ({}): can not check for build loop dangers", workflowsDir);
        } else {
            try {
                AtomicBoolean errorsDetected = new AtomicBoolean();
                Files.list(workflowsDir)
                        .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().matches(".*\\.ya?ml"))
                        .forEach(f -> {
                            try {
                                Map<?, ?> jobs = (Map<?, ?>) Util.readYaml(f).get("jobs");
                                if (jobs == null) {
                                    LOGGER.warn("RECURSION DANGER: the workflow file {} does not contain jobs; is it a workflow file???", f);
                                } else {
                                    jobs.keySet().forEach(jobName -> {
                                        Map<?, ?> job   = (Map<?, ?>) jobs.get(jobName);
                                        String    theIf = (String) job.get("if");
                                        if (theIf == null || !theIf.equals(NO_CI_GUARD)) {
                                            LOGGER.error("RECURSION DANGER: the workflow file {} contains a job '{}' that does not guard against retriggering (add 'if: \"{}\")", f, jobName, NO_CI_GUARD);
                                            errorsDetected.set(true);
                                        }
                                    });
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                if (errorsDetected.get()) {
                    throw new GradleException("RECURSION DANGER in a workflow file");
                }
            } catch (IOException e) {
                throw new GradleException("can not scan workflows dir", e);
            }
        }
    }
}
