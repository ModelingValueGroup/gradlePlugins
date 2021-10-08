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

package org.modelingvalue.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.MPS_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_CLASS_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_PACKAGE_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_GITHUB_WORKFLOW;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_TESTING;
import static org.modelingvalue.gradle.mvgplugin.Info.UPLOADER_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Util.getTestMarker;
import static org.modelingvalue.gradle.mvgplugin.Util.numOccurences;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.mvgplugin.DotProperties;
import org.modelingvalue.gradle.mvgplugin.GitManager;
import org.modelingvalue.gradle.mvgplugin.GitUtil;
import org.modelingvalue.gradle.mvgplugin.Info;

public class MvgPluginTest {
    private static final boolean I_NEED_TO_DEBUG_THIS_TEST = true;
    public static final  String  TEST_WORKSPACE_NAME       = "gradlePlugins";
    private static final Path    testWorkspaceDir          = Paths.get("build", TEST_WORKSPACE_NAME).toAbsolutePath();
    private static final Path    settingsFile              = Paths.get("settings.gradle");
    private static final Path    bashFile                  = Paths.get("bashProduced.txt.corrector.sh");
    private static final Path    buildFile                 = Paths.get("build.gradle.kts");
    private static final Path    gradlePropsFile           = Paths.get("gradle.properties");
    private static final Path    workflowFile              = Paths.get(".github", "workflows", "xyz.yaml");
    private static final Path    dependabotFile            = Paths.get(".github", "dependabot.yml");
    private static final Path    antFile                   = Paths.get("try_build.xml");
    private static final Path    javaFile                  = Paths.get("src", "main", "java", "A.java");
    private static final Path    propFile                  = Paths.get("src", "main", "java", "testCR.properties");
    private static final Path    pruupFile                 = Paths.get("src", "main", "java", "testCRLF.pruuperties");
    private static final Path    headFile                  = Paths.get(".git", "HEAD");
    private static final Path    configFile                = Paths.get(".git", "config");
    private static final Path    dotGitIgnore              = Paths.get(".gitignore");

    @Test
    public void checkId() {
        DotProperties props = new DotProperties(Paths.get("..", GRADLE_PROPERTIES_FILE));

        assertTrue(props.isValid());

        assertEquals(PLUGIN_PACKAGE_NAME, props.getProp("mvgplugin_id"));
        assertEquals(PLUGIN_CLASS_NAME, props.getProp("mvgplugin_class"));
        assertEquals(PLUGIN_NAME, props.getProp("mvgplugin_name"));
    }

    @Test
    public void checkJunitVersion() throws IOException {
        Pattern      pat1  = Pattern.compile("^\\s*test[a-zA-Z]*\\s*\\(\\s*\"" + Pattern.quote(Info.JUNIT_GROUP_ID) + ":[-a-zA-Z]*:[0-9.]*\"\\s*\\)\\s*$");
        Pattern      pat2  = Pattern.compile("^\\s*test[a-zA-Z]*\\s*\\(\\s*\"" + Pattern.quote(Info.JUNIT_GROUP_ID) + ":[-a-zA-Z]*:" + Pattern.quote(Info.JUNIT_VERSION) + "\"\\s*\\)\\s*$");
        List<String> lines = Files.readAllLines(buildFile);
        List<String> wrongLines = lines.stream()
                .filter(l -> pat1.matcher(l).find())
                .filter(l -> !pat2.matcher(l).matches())
                .collect(Collectors.toList());
        if (!wrongLines.isEmpty()) {
            System.err.println("JUNIT_VERSION in Info.java=" + Info.JUNIT_VERSION + ", but there are different versions in my build file (" + buildFile + "):");
            wrongLines.forEach(l -> System.err.println(" - OFFENDING LINE: " + l));
            fail("junit version difference between compiled code and my gradle file");
        }
    }

    @Test
    public void checkPlugin() throws IOException, GitAPIException {
        // need to set these before accessing the Info class!
        System.setProperty(PROP_NAME_TESTING, "" + true);
        System.setProperty(PROP_NAME_GITHUB_WORKFLOW, "build");

        assertNotEquals("notset", Info.ALLREP_TOKEN, "this test needs the ALLREP_TOKEN to succesfully terminate");

        prepareTestWorkspace();

        assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(propFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(headFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(configFile)).contains("Copyright"));

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put(PROP_NAME_TESTING, System.getProperty(PROP_NAME_TESTING));
        env.put(PROP_NAME_GITHUB_WORKFLOW, System.getProperty(PROP_NAME_GITHUB_WORKFLOW));

        // Run the build
        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();
        String       out;
        String       err;
        try {
            GradleRunner gradleRunner = GradleRunner.create();
            if (I_NEED_TO_DEBUG_THIS_TEST) {
                gradleRunner = gradleRunner.withDebug(true);
            } else {
                gradleRunner = gradleRunner.withEnvironment(env);
            }
            gradleRunner
                    .forwardStdOutput(outWriter)
                    .forwardStdError(errWriter)
                    .withPluginClasspath()
                    .withProjectDir(testWorkspaceDir.toFile())
                    .withArguments("--scan", "--info", "--stacktrace", "check", "publish")
                    .build();
        } finally {
            out = outWriter.toString();
            err = errWriter.toString();

            System.out.println("/================================= out ====================================");
            if (0 < out.length()) {
                Arrays.stream(out.replace('\r', '\n').split("\n")).forEach(l -> System.out.println("| " + l));
            }
            System.out.println("+================================= err ====================================");
            if (0 < err.length()) {
                Arrays.stream(err.replace('\r', '\n').split("\n")).forEach(l -> System.out.println("| " + l));
            }
            System.out.println("\\==========================================================================");

            GitUtil.untag(testWorkspaceDir, "v0.0.1", "v0.0.2", "v0.0.3", "v0.0.4");


            DotProperties instance = new DotProperties(testWorkspaceDir.resolve(GRADLE_PROPERTIES_FILE));
            int           m        = 0 < numOccurences("master=true", out) ? 1 : 0;

            // Verify the result
            assertAll(
                    () -> assertEquals("0.0.4", instance.getProp(Info.PROP_NAME_VERSION)),
                    //
                    () -> assertEquals(6, numOccurences("+ mvg: header     regenerated : ", out)),
                    () -> assertEquals(2, numOccurences("+ mvg: eols       regenerated : ", out)),
                    () -> assertEquals(6, numOccurences("+ mvg: eols       untouched   : ", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: found vacant version: 0.0.4 (was 0.0.1)", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: project 'test-name': version: 0.0.1 => 0.0.4, group: test.group => test.group", out)),
                    () -> assertEquals(6, numOccurences(getTestMarker("r+"), out)),
                    () -> assertEquals(41, numOccurences(getTestMarker("r-"), out)),
                    () -> assertEquals(1, numOccurences("+ mvg: adding test.useJUnitPlatform", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: increasing test heap from default to 2g", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: adding junit5 dependencies", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: agreeing to buildScan", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: adding tasks for javadoc & source jars", out)),
                    () -> assertEquals(1, numOccurences("+ mvg: setting java source&target compatibility from (11&11) to 11", out)),
                    () -> assertEquals(1, numOccurences("+ mvg-mps: the MPS build number 203.5981.1014 of MPS 2020.3 is in range [111.222...333.444.555] of the requested in ant file", out)),
                    () -> assertEquals(3, numOccurences("+ mvg-mps: dependency     replaced: ", out)),
                    () -> assertEquals(1, numOccurences("+ mvg-git:" + TEST_WORKSPACE_NAME + ": staging changes (adds=9 rms=0; branch=", out)),
                    () -> assertEquals(1 + m, numOccurences("+ mvg-git:" + TEST_WORKSPACE_NAME + ": NOT pushing, repo has no remotes", out)),
                    () -> assertEquals(2, numOccurences(getTestMarker("t"), out)),
                    () -> assertEquals(4, numOccurences(getTestMarker("triggers"), out)),
                    () -> assertEquals(1 - m, numOccurences("+ mvg: not tagging this version with 'v0.0.4' because this is not the master branch", out)),
                    () -> assertEquals(m, numOccurences("+ mvg: tagging this version with 'v0.0.4' because this is the master branch", out)),
                    () -> assertEquals(1, numOccurences(getTestMarker("!"), out)),
                    //
                    () -> assertTrue(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\nversion=0.0.4\n")),
                    () -> assertTrue(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright")),
                    () -> assertTrue(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright")),
                    () -> assertTrue(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright")),
                    () -> assertTrue(Files.readString(testWorkspaceDir.resolve(propFile)).contains("Copyright")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("Copyright")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(headFile)).contains("Copyright")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(configFile)).contains("Copyright")),
                    //
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\r")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("\r")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("\r")),
                    () -> assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("\r")),
                    //
                    () -> assertEquals(264, Files.readString(testWorkspaceDir.resolve(dependabotFile)).length()),
                    //
                    () -> assertEquals(0, Files.readString(testWorkspaceDir.resolve(propFile)).replaceAll("[^\r]", "").length()),
                    () -> assertEquals(17, Files.readString(testWorkspaceDir.resolve(propFile)).replaceAll("[^\n]", "").length()),
                    () -> assertEquals(17, Files.readAllLines(testWorkspaceDir.resolve(propFile)).size()),
                    //
                    () -> assertEquals(0, Files.readString(testWorkspaceDir.resolve(pruupFile)).replaceAll("[^\r]", "").length()),
                    () -> assertEquals(1, Files.readString(testWorkspaceDir.resolve(pruupFile)).replaceAll("[^\n]", "").length()),
                    () -> assertEquals(2, Files.readAllLines(testWorkspaceDir.resolve(pruupFile)).size())
            );
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Setup the test build
    private void prepareTestWorkspace() throws IOException, GitAPIException {
        String sourceBranch;
        try (Git sourceGit = GitManager.git(Paths.get("."))) {
            sourceBranch = sourceGit.getRepository().getBranch();
        }
        if (Files.isDirectory(testWorkspaceDir)) {
            FileUtils.delete(testWorkspaceDir.toFile(), FileUtils.RECURSIVE);
        }
        Git.init()
                .setDirectory(testWorkspaceDir.toFile())
                .setInitialBranch(sourceBranch)
                .call()
                .close();

        cp(null, bashFile, settingsFile, javaFile, gradlePropsFile, workflowFile, antFile);
        cp(s -> s
                        .replaceAll("~myPackage~", PLUGIN_PACKAGE_NAME)
                        .replaceAll("~myMvgCorrectorExtension~", CORRECTOR_TASK_NAME)
                        .replaceAll("~myMvgMpsExtension~", MPS_TASK_NAME)
                        .replaceAll("~myMvgUploaderExtension~", UPLOADER_TASK_NAME)
                , buildFile);
        cp(s -> s.replaceAll("\n", "\r"), propFile);
        cp(s -> s.replaceAll("\n", "\r\n"), pruupFile);
        Files.writeString(testWorkspaceDir.resolve(dotGitIgnore), "build\n.gradle\n");

        GitUtil.stageCommitPush(testWorkspaceDir, "test-message");
        GitUtil.tag(testWorkspaceDir, "v0.0.1");
        GitUtil.tag(testWorkspaceDir, "v0.0.2");
        GitUtil.tag(testWorkspaceDir, "v0.0.3");
    }

    private static void cp(Function<String, String> postProcess, Path... fs) throws IOException {
        for (Path f : fs) {
            String name = f.getFileName().toString();
            try (InputStream in = MvgPluginTest.class.getResourceAsStream(name)) {
                try (Scanner scanner = new Scanner(Objects.requireNonNull(in), UTF_8).useDelimiter("\\A")) {
                    String content = scanner.hasNext() ? scanner.next() : "";
                    if (postProcess != null) {
                        content = postProcess.apply(content);
                    }
                    Path inTmp = testWorkspaceDir.resolve(f);
                    Files.createDirectories(inTmp.getParent());
                    Files.deleteIfExists(inTmp);
                    Files.writeString(inTmp, content);
                }
            }
        }
    }
}
