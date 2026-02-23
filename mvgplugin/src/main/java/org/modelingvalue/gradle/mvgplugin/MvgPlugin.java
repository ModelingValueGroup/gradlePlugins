//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import static org.modelingvalue.gradle.mvgplugin.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.CI;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MIN_TEST_HEAP_SIZE;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_ALLREP_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_CI;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION_JAVA;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isDevelopBranch;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMasterBranch;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMvgCI_orTesting;
import static org.modelingvalue.gradle.mvgplugin.Util.toBytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.process.CommandLineArgumentProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MvgPlugin implements Plugin<Project> {
    public static MvgPlugin singleton;

    private final BuildEventsListenerRegistry buildEventsListenerRegistry;
    private boolean               inactiveBecauseNotRootProject;
    private Gradle                gradle;
    private Extension             ext;
    private MvgCorrector          mvgCorrector;
    private MvgTagger             mvgTagger;
    private MvgBranchBasedBuilder mvgBranchBasedBuilder;
    private MvgMps                mvgMps;
    private MvgUploader           mvgUploader;
    private boolean               traceHeaderDone;

    public abstract static class Extension {
        public static Extension make(Gradle gradle) {
            Extension ext = gradle.getRootProject().getExtensions().create(MVG, Extension.class);
            ext.getVerboseTaskExecution().convention(true);
            ext.getPrepTestsForJunit5().convention(true);
            ext.getPrepJavacForLint().convention(true);
            ext.getPrepJavadocForLint().convention(true);
            ext.getPrepJavacForEncoding().convention(true);
            ext.getPrepJavadocForEncoding().convention(true);
            ext.getMakeJavadocAndSources().convention(true);
            ext.getAddMvgGithubRepositories().convention(true);
            return ext;
        }

        public abstract Property<Boolean> getVerboseTaskExecution();
        public abstract Property<Boolean> getPrepTestsForJunit5();
        public abstract Property<Boolean> getPrepJavacForLint();
        public abstract Property<Boolean> getPrepJavadocForLint();
        public abstract Property<Boolean> getPrepJavacForEncoding();
        public abstract Property<Boolean> getPrepJavadocForEncoding();
        public abstract Property<Boolean> getMakeJavadocAndSources();
        public abstract Property<Boolean> getAddMvgGithubRepositories();
    }

    @Inject
    public MvgPlugin(BuildEventsListenerRegistry buildEventsListenerRegistry) {
        singleton = this;
        this.buildEventsListenerRegistry = buildEventsListenerRegistry;
    }

    public void apply(Project project) {
        LOGGER.info("+ mvg: apply {} on project {}", getClass().getSimpleName(), project.getName());
        gradle = project.getGradle();
        Project rootProject = gradle.getRootProject();
        InfoGradle.setGradle(rootProject.getRootDir().toPath().toAbsolutePath(), rootProject.getName());

        inactiveBecauseNotRootProject = rootProject != project;
        if (inactiveBecauseNotRootProject) {
            LOGGER.error("mvgplugin: the plugin {} can only be applied to the root project ({})", getClass().getSimpleName(), rootProject);
            //throw new GradleException("the plugin " + getClass().getSimpleName() + " can only be applied to the root project (" + gradle.getRootProject().getName() + ")");
        } else {
            ext = Extension.make(gradle);
            BranchParameterNames.init();

            LOGGER.info("+ mvg: MvgPlugin.apply to project {}", project.getName());
            LOGGER.info("+ mvg: {}={}, {}={} {}={}, {}={}, {}={}",
                    PROP_NAME_CI, CI,
                    "MVGCI|TEST", isMvgCI_orTesting(),
                    "master", isMasterBranch(),
                    "develop", isDevelopBranch(),
                    PROP_NAME_ALLREP_TOKEN, Util.hide(ALLREP_TOKEN));

            checkWorkflowFilesForLoopingDanger();
            checkIfWeAreUsingTheLatestPluginVersion();

            trace();

            listenForTaskExecution();
            tuneTesting();
            tuneJavaPlugin();
            tuneJavacPlugin();
            tuneJavaDocPlugin();
            tuneJavaEncoding();
            addMVGRepositories();

            mvgCorrector = new MvgCorrector(gradle);
            mvgTagger = new MvgTagger(gradle);
            mvgBranchBasedBuilder = new MvgBranchBasedBuilder(gradle, buildEventsListenerRegistry);
            mvgMps = new MvgMps(gradle);
            mvgUploader = new MvgUploader(gradle);
        }
    }

    @NotNull
    public Object resolveMpsDependency(@NotNull String dep) {
        // TODO use SelfResolvingDependency
        //        new SelfResolvingDependency(){
        //            @Nullable
        //            @Override
        //            public String getGroup() {
        //                return null;
        //            }
        //
        //            @Override
        //            public String getName() {
        //                return null;
        //            }
        //
        //            @Nullable
        //            @Override
        //            public String getVersion() {
        //                return null;
        //            }
        //
        //            @Override
        //            public boolean contentEquals(Dependency dependency) {
        //                return false;
        //            }
        //
        //            @Override
        //            public Dependency copy() {
        //                return null;
        //            }
        //
        //            @Nullable
        //            @Override
        //            public String getReason() {
        //                return null;
        //            }
        //
        //            @Override
        //            public void because(@Nullable String s) {
        //
        //            }
        //
        //            @Override
        //            public TaskDependency getBuildDependencies() {
        //                return null;
        //            }
        //
        //            @Override
        //            public Set<File> resolve() {
        //                return mvgMps.resolveMpsDependency(dep);
        //            }
        //
        //            @Override
        //            public Set<File> resolve(boolean b) {
        //                return resolve();
        //            }
        //        };
        return mvgMps.resolveMpsDependency(dep);
    }

    private void checkWorkflowFilesForLoopingDanger() {
        Path workflowsDir = InfoGradle.getWorkflowsDir();
        if (Files.isDirectory(workflowsDir)) {
            try {
                AtomicBoolean errorsDetected = new AtomicBoolean();
                Files.list(workflowsDir)
                        .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().matches(".*\\.ya?ml"))
                        .forEach(f -> {
                            try {
                                Map<?, ?> jobs = (Map<?, ?>) Util.readYaml(f).get("jobs");
                                if (jobs == null) {
                                    LOGGER.warn("+ mvg: RECURSION DANGER: the workflow file {} does not contain jobs; is it a workflow file???", f);
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
            LOGGER.info("+ mvg: can not determine if using the latest version of mvg plugin: can not determine running version");
        } else {
            MavenMetaVersionExtractor extractor = new MavenMetaVersionExtractor(Info.PLUGIN_META_URL);
            if (extractor.error()) {
                LOGGER.warn("+ mvg: can not determine if using the latest version of mvg plugin: metainfo of myself not readable ({}, msg={})", Info.PLUGIN_META_URL, extractor.getException().getMessage());
            } else if (!extractor.getLatest().equals(v)) {
                LOGGER.warn("+ mvg: NOT using the latest mvg plugin version (using {}, latest is {})", v, extractor.getLatest());
            } else {
                LOGGER.info("+ mvg: OK: using the latest mvg plugin version ({})", v);
            }
        }
    }

    private void trace() {
        if (LOGGER.isDebugEnabled()) {
            gradle.afterProject(p -> {
                if (!traceHeaderDone) {
                    traceHeaderDone = true;
                    LOGGER.debug("++-------------------------------------------------------------------------------------------------------------------------------------");
                    LOGGER.debug("++ mvg: found                {} {} {}", String.format("%-30s", "NAME"), String.format("%-30s", "PROJECT"), "CLASS");
                    LOGGER.debug("++-------------------------------------------------------------------------------------------------------------------------------------");
                }
                String projectName = String.format("%-30s", p.getName());
                // Eager iteration is intentional here: trace() is diagnostic and must see all existing elements
                p.getExtensions().getExtensionsSchema().forEach(schema -> LOGGER.debug("++ mvg: found extension    : {} {} {}", String.format("%-30s", schema.getName()), projectName, schema.getPublicType().getSimpleName()));
                p.getTasks().all(task ->                                   LOGGER.debug("++ mvg: found task         : {} {} {}", String.format("%-30s", task.getName()), projectName, task.getClass().getSimpleName()));
                p.getConfigurations().all(conf ->                          LOGGER.debug("++ mvg: found configuration: {} {} {} #artifacts={}", String.format("%-30s", conf.getName()), projectName, conf.getClass().getSimpleName(), conf.getAllArtifacts().size()));
                p.getPlugins().all(plugin -> /*                                         */LOGGER.debug("++ mvg: found plugin       : {} {}", /*             */String.format("%-30s", plugin.getClass().getSimpleName()), projectName));
            });
        }
    }

    private void listenForTaskExecution() {
        // Note: unlike the old TaskExecutionListener, doFirst/doLast only fires for tasks that
        // actually execute work — not for UP-TO-DATE or SKIPPED tasks.
        gradle.afterProject(p -> {
            if (ext.getVerboseTaskExecution().get()) {
                p.getTasks().configureEach(task -> {
                    task.doFirst(s -> LOGGER.info("+ mvg: >>>>> {}", task.getName()));
                    task.doLast(s -> LOGGER.info("+ mvg: <<<<< {}\n", task.getName()));
                });
            }
        });
    }

    private void tuneTesting() {
        gradle.afterProject(p -> {
            if (ext.getPrepTestsForJunit5().get()) {
                p.getTasks().withType(Test.class).configureEach(test -> {
                    LOGGER.info("+ mvg: adding test.useJUnitPlatform");
                    test.useJUnitPlatform();

                    if (test.getMaxHeapSize() == null || toBytes(test.getMaxHeapSize()) < toBytes(MIN_TEST_HEAP_SIZE)) {
                        LOGGER.info("+ mvg: increasing test heap from {} to {}", test.getMaxHeapSize() == null ? "default" : test.getMaxHeapSize(), MIN_TEST_HEAP_SIZE);
                        test.setMaxHeapSize(MIN_TEST_HEAP_SIZE);
                    }
                });

                Object java = p.getExtensions().findByName("java");
                if (java != null) {
                    LOGGER.info("+ mvg: adding junit5 dependencies");
                    Info.JUNIT_IMPLEMENTATION_DEPS.forEach(dep -> p.getDependencies().add("testImplementation", dep));
                    Info.JUNIT_RUNTIMEONLY_DEPS.forEach(dep -> p.getDependencies().add("testRuntimeOnly", dep));
                }
            }
        });
    }

    private void tuneJavacPlugin() {
        gradle.afterProject(p -> {
            if (ext.getPrepJavacForLint().get()) {
                @SuppressWarnings("Convert2Lambda") // keep this, grdale does not like java lambdas here 8- see: https://docs.gradle.org/7.2/userguide/validation_problems.html#implementation_unknown
                CommandLineArgumentProvider adder = new CommandLineArgumentProvider() {
                    @Override
                    public Iterable<String> asArguments() {
                        return List.of("-Xlint:unchecked", "-Xlint:deprecation");
                    }
                };
                p.getTasks()
                        .withType(JavaCompile.class)
                        .configureEach(javaCompile -> javaCompile.getOptions().getCompilerArgumentProviders().add(adder));
            }
        });
    }

    private void tuneJavaPlugin() {
        String javaVersionInProps = getGradleDotProperties().getProp(PROP_NAME_VERSION_JAVA, Info.JAVA_VERSION);
        if (javaVersionInProps == null) {
            LOGGER.info("+ mvg: java version not adjusted because there is no {} property in {}", PROP_NAME_VERSION_JAVA, getGradleDotProperties().getFile());
        }
        gradle.afterProject(p -> {
            JavaPluginExtension javaExt = (JavaPluginExtension) p.getExtensions().findByName("java");
            if (javaExt != null) {
                if (ext.getMakeJavadocAndSources().get()) {
                    LOGGER.info("+ mvg: adding tasks for javadoc & source jars");
                    javaExt.withJavadocJar();
                    javaExt.withSourcesJar();
                }

                if (javaVersionInProps != null) {
                    JavaVersion current   = JavaVersion.current();
                    JavaVersion requested = JavaVersion.toVersion(javaVersionInProps);
                    if (!current.isCompatibleWith(requested)) {
                        LOGGER.error("mvgplugin: the requested java version ({} in gradle.properties = {}) is not compatible with the running java version ({}). continuing with {}", PROP_NAME_VERSION_JAVA, requested, current, current);
                    } else {
                        LOGGER.info("+ mvg: setting java source&target compatibility from ({}&{}) to {}", javaExt.getSourceCompatibility(), javaExt.getTargetCompatibility(), requested);
                        javaExt.setSourceCompatibility(requested);
                        javaExt.setTargetCompatibility(requested);
                    }
                }
            }
        });
    }

    private void tuneJavaDocPlugin() {
        gradle.afterProject(p -> {
            if (ext.getPrepJavadocForLint().get()) {
                TaskCollection<Javadoc> javadocTasks = p.getTasks().withType(Javadoc.class);
                javadocTasks.configureEach(jd -> jd.options(opt -> {
                    if (opt instanceof StandardJavadocDocletOptions) {
                        LOGGER.info("+ mvg: adding javadoc option to ignore warnings");
                        ((StandardJavadocDocletOptions) opt).addStringOption("Xdoclint:none", "-quiet");
                    }
                }));
            }
        });
    }

    private void tuneJavaEncoding() {
        String utf8 = StandardCharsets.UTF_8.name();
        gradle.afterProject(p -> {
            if (ext.getPrepJavacForEncoding().get()) {
                p.getTasks()
                        .withType(JavaCompile.class)
                        .configureEach(javac -> {
                            CompileOptions options = javac.getOptions();
                            if (!utf8.equals(options.getEncoding())) {
                                LOGGER.info("+ mvg: setting {} encoding from {} to {}", javac.getName(), options.getEncoding(), utf8);
                                options.setEncoding(utf8);
                            }
                        });
            }
            if (ext.getPrepJavadocForEncoding().get()) {
                p.getTasks()
                        .withType(Javadoc.class)
                        .configureEach(t -> {
                            MinimalJavadocOptions options = t.getOptions();
                            if (!utf8.equals(options.getEncoding())) {
                                LOGGER.info("+ mvg: setting {} encoding from {} to {}", t.getName(), options.getEncoding(), utf8);
                                options.setEncoding(utf8);
                            }
                        });
            }
        });
    }

    private void addMVGRepositories() {
        gradle.allprojects(p -> {
            if (ext.getAddMvgGithubRepositories().get()) {
                LOGGER.info("+ mvg: adding MVG repositories to project {}", p.getName());

                p.getRepositories().mavenCentral();
                p.getRepositories().mavenLocal();
                p.getRepositories().maven(InfoGradle.getGithubMavenRepoMaker(true));
                p.getRepositories().maven(InfoGradle.getGithubMavenRepoMaker(false));

                p.getRepositories().forEach(r -> LOGGER.info("+ mvg:   - {}", r.getName()));
            }
        });
    }
}
