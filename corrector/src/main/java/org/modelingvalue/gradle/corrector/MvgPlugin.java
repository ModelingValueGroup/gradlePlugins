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
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.extensibility.DefaultConvention;

import com.gradle.scan.plugin.BuildScanExtension;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MvgPlugin implements Plugin<Project> {
    public static final String NO_CI_GUARD = "!contains(github.event.head_commit.message, '[no-ci]')";

    private Gradle             gradle;
    private Corrector          corrector;
    private Tagger             tagger;
    private BranchBasedBuilder branchBasedBuilder;

    public void apply(Project project) {
        gradle = project.getGradle();

        checkMustBeRootProject(project);
        checkWorkflowFilesForLoopingDanger();

        trace();

        useJUnitPlatform();
        agreeToBuildScan();
        tuneJavaPlugin();
        addMVGRepositories();

        corrector = new Corrector(gradle);
        tagger = new Tagger(gradle);
        branchBasedBuilder = new BranchBasedBuilder(gradle);
    }

    private void trace() {
        gradle.afterProject(p -> {
            LOGGER.info("+ all extension of project {}:", p.getName());
            ((DefaultConvention) p.getExtensions()).getAsMap().forEach((k, v) -> LOGGER.info("+   ext - {} = {}", String.format("%-20s", k), v.getClass()));
            LOGGER.info("+ all tasks of project {}:", p.getName());
            p.getTasks().all(t -> LOGGER.info("+   tsk - {} = {}", String.format("%-20s", t.getName()), t.getClass()));
        });
    }

    private void useJUnitPlatform() {
        gradle.afterProject(p -> {
            Test test = (Test) p.getTasks().findByName("test");
            if (test != null) {
                LOGGER.info("+ adding test.useJUnitPlatform");
                test.useJUnitPlatform();
            }

            p.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.6.2");
            p.getDependencies().add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.7.0");
        });
    }

    private void agreeToBuildScan() {
        gradle.afterProject(p -> {
            BuildScanExtension buildScan = (BuildScanExtension) p.getExtensions().findByName("buildScan");
            if (buildScan != null) {
                LOGGER.info("+ agreeing to buildScan");
                buildScan.setTermsOfServiceAgree("yes");
                buildScan.setTermsOfServiceUrl("https://gradle.com/terms-of-service");
            }
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private void tuneJavaPlugin() {
        gradle.afterProject(p -> {
            JavaPluginExtension java = (JavaPluginExtension) p.getExtensions().findByName("java");
            if (java != null) {
                LOGGER.info("+ adding tasks for javadoc & source jars");
                java.withJavadocJar();
                java.withSourcesJar();
            }
        });
    }

    private void addMVGRepositories() {
        gradle.allprojects(p -> {
            LOGGER.info("+ adding MVG repositories to project {}", p.getName());

            p.getRepositories().jcenter();
            p.getRepositories().mavenLocal();
            p.getRepositories().maven(Info.MVG_MAVEN_REPO_MAKER);
            p.getRepositories().maven(Info.MVG_MAVEN_SNAPSHOTS_REPO_MAKER);

            p.getRepositories().forEach(r -> LOGGER.info("+   - {}", r.getName()));
        });
    }

    private void checkMustBeRootProject(Project project) {
        String pluginName = this.getClass().getSimpleName();
        LOGGER.info("+ apply {} on project {}", pluginName, project.getName());
        if (gradle.getRootProject() != project) {
            LOGGER.error("the plugin {} can only be applied to the root project ({})", pluginName, gradle.getRootProject());
            throw new GradleException("the plugin " + pluginName + " can only be applied to the root project (" + gradle.getRootProject().getName() + ")");
        }
    }

    private void checkWorkflowFilesForLoopingDanger() {
        Path root         = gradle.getRootProject().getRootDir().toPath();
        Path workflowsDir = root.resolve(".github").resolve("workflows");
        if (!Files.isDirectory(workflowsDir)) {
            LOGGER.warn("can not check for BUILD LOOP DANGER: workflows dir not found at {}", workflowsDir);
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
                    throw new GradleException("BUILD LOOP DANGER in one or more workflow files");
                }
            } catch (IOException e) {
                throw new GradleException("can not scan workflows dir", e);
            }
        }
    }
}
