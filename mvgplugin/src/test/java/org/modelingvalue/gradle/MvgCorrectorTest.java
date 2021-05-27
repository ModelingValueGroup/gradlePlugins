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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.modelingvalue.gradle.mvgplugin.GradleDotProperties.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.MPS_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_CLASS_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.PLUGIN_PACKAGE_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.UPLOADER_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Util.numOccurences;

import java.io.File;
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

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.mvgplugin.GitUtil;
import org.modelingvalue.gradle.mvgplugin.GradleDotProperties;
import org.modelingvalue.gradle.mvgplugin.Info;

public class MvgCorrectorTest {
    private static final Path testWorkspaceDir = Paths.get("build", "testWorkspace").toAbsolutePath();
    private static final Path settingsFile     = Paths.get("settings.gradle");
    private static final Path buildFile        = Paths.get("build.gradle.kts");
    private static final Path gradlePropsFile  = Paths.get("gradle.properties");
    private static final Path yamlFile         = Paths.get(".github", "workflows", "xyz.yaml");
    private static final Path antFile          = Paths.get("try_build.xml");
    private static final Path headFile         = Paths.get(".git", "HEAD");
    private static final Path javaFile         = Paths.get("src", "main", "java", "A.java");
    private static final Path propFile         = Paths.get("src", "main", "java", "testCR.properties");
    private static final Path pruupFile        = Paths.get("src", "main", "java", "testCRLF.pruuperties");

    @Test
    public void checkId() {
        GradleDotProperties.init(new File(".."));

        assertTrue(getGradleDotProperties().isValid());

        assertEquals(PLUGIN_PACKAGE_NAME, getGradleDotProperties().getProp("mvgplugin_id", null));
        assertEquals(PLUGIN_CLASS_NAME, getGradleDotProperties().getProp("mvgplugin_class", null));
        assertEquals(PLUGIN_NAME, getGradleDotProperties().getProp("mvgplugin_name", null));
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
    public void checkFunctionality() throws IOException {
        // Setup the test build
        cp(null, settingsFile, javaFile, gradlePropsFile, headFile, yamlFile, antFile);
        cp(s -> s
                        .replaceAll("~myPackage~", PLUGIN_PACKAGE_NAME)
                        .replaceAll("~myMvgCorrectorExtension~", CORRECTOR_TASK_NAME)
                        .replaceAll("~myMvgMpsExtension~", MPS_TASK_NAME)
                        .replaceAll("~myMvgUploaderExtension~", UPLOADER_TASK_NAME)
                , buildFile);
        cp(s -> s.replaceAll("\n", "\r"), propFile);
        cp(s -> s.replaceAll("\n", "\n\r"), pruupFile);

        // prepare git tags:
        GitUtil.untag(testWorkspaceDir, "v0.0.1", "v0.0.2", "v0.0.3", "v0.0.4");
        GitUtil.tag(testWorkspaceDir, "v0.0.1");
        GitUtil.tag(testWorkspaceDir, "v0.0.2");
        GitUtil.tag(testWorkspaceDir, "v0.0.3");

        assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(propFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(headFile)).contains("Copyright"));

        Map<String, String> env = new HashMap<>(System.getenv());
        env.putIfAbsent(Info.PROP_NAME_ALLREP_TOKEN, "DRY");

        // Run the build
        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();
        String       out;
        String       err;
        try {
            //noinspection UnstableApiUsage
            GradleRunner.create()
                    //.withDebug(true)            // use this     if you need to debug
                    .withEnvironment(env)     // use this not if you need to debug
                    .forwardStdOutput(outWriter)
                    .forwardStdError(errWriter)
                    .withPluginClasspath()
                    .withProjectDir(testWorkspaceDir.toFile())
                    .withArguments("--scan", "--info", "--stacktrace", "check")
                    .build();
        } finally {
            out = outWriter.toString();
            err = errWriter.toString();

            System.out.println("/================================= out ====================================");
            Arrays.stream(out.split("\n")).forEach(l -> System.out.println("| " + l));
            System.out.println("+================================= err ====================================");
            Arrays.stream(err.split("\n")).forEach(l -> System.out.println("| " + l));
            System.out.println("\\==========================================================================");

            GitUtil.untag(testWorkspaceDir, "v0.0.1", "v0.0.2", "v0.0.3", "v0.0.4");
            GradleDotProperties.init(testWorkspaceDir.toFile());
        }

        // Verify the result
        assertAll(
                () -> assertEquals("0.0.4", getGradleDotProperties().getProp(Info.PROP_NAME_VERSION, null)),
                //
                () -> assertEquals(5, numOccurences("+ header regenerated : ", out)),
                () -> assertEquals(2, numOccurences("+ eols   regenerated : ", out)),
                () -> assertEquals(5, numOccurences("+ eols   untouched   : ", out)),
                () -> assertEquals(1, numOccurences("+ found vacant version: 0.0.4 (was 0.0.1)", out)),
                () -> assertEquals(1, numOccurences("+ project 'testWorkspace': version: 0.0.1 => 0.0.4, group: group => group", out)),
                () -> assertEquals(3, numOccurences("+ bbb: dependency     replaced: ", out)),
                () -> assertEquals(36, numOccurences("+ bbb: dependency NOT replaced: ", out)),
                () -> assertEquals(1, numOccurences("+ adding test.useJUnitPlatform", out)),
                () -> assertEquals(1, numOccurences("+ increasing test heap from default to 2g", out)),
                () -> assertEquals(1, numOccurences("+ adding junit5 dependencies", out)),
                () -> assertEquals(1, numOccurences("+ agreeing to buildScan", out)),
                () -> assertEquals(1, numOccurences("+ adding tasks for javadoc & source jars", out)),
                () -> assertEquals(1, numOccurences("+ setting java source&target compatibility from (11&11) to 11", out)),
                () -> assertEquals(1, numOccurences("+ the MPS build number 203.5981.1014 of MPS 2020.3 is in range [111.222...333.444.555] of the requested in ant file", out)),
                () -> assertEquals(3, numOccurences("+ MPS: dependency     replaced: ", out)),
                //
                () -> assertTrue(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\nversion=0.0.4\n")),
                () -> assertTrue(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright")),
                () -> assertTrue(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright")),
                () -> assertTrue(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright")),
                () -> assertTrue(Files.readString(testWorkspaceDir.resolve(propFile)).contains("Copyright")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("Copyright")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(headFile)).contains("Copyright")),
                //
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\r")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("\r")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("\r")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("\r")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(propFile)).contains("\r")),
                () -> assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("\r"))
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static void cp(Function<String, String> postProcess, Path... fs) throws IOException {
        for (Path f : fs) {
            String name = f.getFileName().toString();
            try (InputStream in = MvgCorrectorTest.class.getResourceAsStream(name)) {
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
