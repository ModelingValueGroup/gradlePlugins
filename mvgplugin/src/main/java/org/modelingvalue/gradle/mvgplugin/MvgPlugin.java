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

package org.modelingvalue.gradle.mvgplugin;

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.GradleDotProperties.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.CI;
import static org.modelingvalue.gradle.mvgplugin.Info.MIN_TEST_HEAP_SIZE;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_CI;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION_JAVA;
import static org.modelingvalue.gradle.mvgplugin.Info.isDevelopBranch;
import static org.modelingvalue.gradle.mvgplugin.Info.isMasterBranch;
import static org.modelingvalue.gradle.mvgplugin.Util.toBytes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.internal.extensibility.DefaultConvention;
import org.jetbrains.annotations.NotNull;

import com.gradle.scan.plugin.BuildScanExtension;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MvgPlugin implements Plugin<Project> {
    public static MvgPlugin singleton;

    private Gradle             gradle;
    private MvgCorrector       mvgCorrector;
    private MvgTagger          mvgTagger;
    private BranchBasedBuilder branchBasedBuilder;
    private MvgMps             mvgMps;

    public MvgPlugin() {
        singleton = this;
    }

    public void apply(Project project) {
        gradle = project.getGradle();
        GradleDotProperties.init(gradle.getRootProject().getRootDir());

        LOGGER.info("MvgPlugin.apply to project {}", project.getName());
        Info.LOGGER.info("+ {}={}, {}={}, {}={}, {}={}", PROP_NAME_CI, CI, "master", isMasterBranch(gradle), "develop", isDevelopBranch(gradle), PROP_NAME_ALLREP_TOKEN, ALLREP_TOKEN);

        checkMustBeRootProject(project);
        checkWorkflowFilesForLoopingDanger();
        checkIfWeAreUsingTheLatestPluginVersion();

        trace();

        tuneTesting();
        tuneJavaPlugin();
        tuneJavaDocPlugin();
        agreeToBuildScan();
        addMVGRepositories();

        mvgCorrector = new MvgCorrector(gradle);
        mvgTagger = new MvgTagger(gradle);
        branchBasedBuilder = new BranchBasedBuilder(gradle);
        mvgMps = new MvgMps(gradle);
    }

    @NotNull
    public Object resolveMpsDependency(@NotNull String dep) {
        return mvgMps.resolveMpsDependency(dep);
    }


    private void checkMustBeRootProject(Project project) {
        String pluginName = this.getClass().getSimpleName();
        LOGGER.info("+ apply {} on project {}", pluginName, project.getName());
        if (gradle.getRootProject() != project) {
            LOGGER.error("mvgplugin: the plugin {} can only be applied to the root project ({})", pluginName, gradle.getRootProject());
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
                                    LOGGER.warn("mvgplugin: RECURSION DANGER: the workflow file {} does not contain jobs; is it a workflow file???", f);
                                } else {
                                    jobs.keySet().forEach(jobName -> {
                                        Map<?, ?> job   = (Map<?, ?>) jobs.get(jobName);
                                        String    theIf = (String) job.get("if");
                                        if (theIf == null || !theIf.equals(Info.NO_CI_GUARD)) {
                                            LOGGER.error("RECURSION DANGER: the workflow file {} contains a job '{}' that does not guard against retriggering (add 'if: \"{}\")", f, jobName, Info.NO_CI_GUARD);
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

    private void checkIfWeAreUsingTheLatestPluginVersion() {
        Version v = Util.getMyPluginVersion();
        if (v == null) {
            LOGGER.info("+ can not determine if using the latest version of mvg plugin: can not determine running version");
        } else {
            MavenMetaVersionExtractor extractor = new MavenMetaVersionExtractor(Info.PLUGIN_META_URL);
            if (extractor.error()) {
                LOGGER.warn("+ can not determine if using the latest version of mvg plugin: metainfo of myself not readable ({}, msg={})", Info.PLUGIN_META_URL, extractor.getException().getMessage());
            } else if (!extractor.getLatest().equals(v)) {
                LOGGER.warn("+ NOT using the latest mvg plugin version (using {}, latest is {})", v, extractor.getLatest());
            } else {
                LOGGER.info("+ OK: using the latest mvg plugin version ({})", v);
            }
        }
    }

    private void trace() {
        gradle.afterProject(p -> {
            String projectName = String.format("%30s", p.getName());
            ((DefaultConvention) p.getExtensions()).getAsMap().forEach((k, v) -> LOGGER.info("++++ {}.ext  [{}] = {}", projectName, String.format("%-30s", k), v.getClass()));
            p.getTasks().all(x -> LOGGER.info("++++ {}.task [{}] = {}", projectName, String.format("%-30s", x.getName()), x.getClass()));
            p.getConfigurations().all(x -> LOGGER.info("++++ {}.conf [{}] = {} #{}", projectName, String.format("%-30s", x.getName()), x.getClass(), x.getAllArtifacts().size()));
            p.getPlugins().all(x -> LOGGER.info("++++ {}.plugin = {}", projectName, x.getClass()));
        });
    }

    private void tuneTesting() {
        gradle.afterProject(p -> {
            Task t = p.getTasks().findByName("test");
            if (t instanceof Test) {
                Test test = (Test) t;

                LOGGER.info("+ adding test.useJUnitPlatform");
                test.useJUnitPlatform();

                if (test.getMaxHeapSize() == null || toBytes(test.getMaxHeapSize()) < toBytes(MIN_TEST_HEAP_SIZE)) {
                    LOGGER.info("+ increasing test heap from {} to {}", test.getMaxHeapSize() == null ? "default" : test.getMaxHeapSize(), MIN_TEST_HEAP_SIZE);
                    test.setMaxHeapSize(MIN_TEST_HEAP_SIZE);
                }

                Object java = p.getExtensions().findByName("java");
                if (java != null) {
                    LOGGER.info("+ adding junit5 dependencies");
                    p.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.6.2");
                    p.getDependencies().add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.7.0");
                }
            } else if (t != null) {
                LOGGER.info("+ 'test' task is not of type Test (but of type '{}')", t.getClass().getSimpleName());
            }
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private void tuneJavaPlugin() {
        String javaVersionInProps = getGradleDotProperties().getProp(PROP_NAME_VERSION_JAVA, "11");
        if (javaVersionInProps == null) {
            LOGGER.info("java version not adjusted because there is no {} property in {}", PROP_NAME_VERSION_JAVA, getGradleDotProperties().getFile());
        }
        gradle.afterProject(p -> {
            JavaPluginExtension java = (JavaPluginExtension) p.getExtensions().findByName("java");
            if (java != null) {
                LOGGER.info("+ adding tasks for javadoc & source jars");
                java.withJavadocJar();
                java.withSourcesJar();

                if (javaVersionInProps != null) {
                    JavaVersion current   = JavaVersion.current();
                    JavaVersion requested = JavaVersion.toVersion(javaVersionInProps);
                    if (!current.isCompatibleWith(requested)) {
                        LOGGER.error("mvgplugin: the requested java version ({} in gradle.properties = {}) is not compatible with the running java version ({}). continuing with {}", PROP_NAME_VERSION_JAVA, requested, current, current);
                    } else {
                        LOGGER.info("+ setting java source&target compatibility from ({}&{}) to {}", java.getSourceCompatibility(), java.getTargetCompatibility(), requested);
                        java.setSourceCompatibility(requested);
                        java.setTargetCompatibility(requested);
                    }
                }
            }
        });
    }

    private void tuneJavaDocPlugin() {
        gradle.afterProject(p -> {
            TaskCollection<Javadoc> javadocsTask = p.getTasks().withType(Javadoc.class);
            javadocsTask.forEach(jd -> jd.options(opt -> {
                if (opt instanceof StandardJavadocDocletOptions) {
                    LOGGER.info("+ adding javadoc option to ignore warnings");
                    ((StandardJavadocDocletOptions) opt).addStringOption("Xdoclint:none", "-quiet");
                }
            }));
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
}
