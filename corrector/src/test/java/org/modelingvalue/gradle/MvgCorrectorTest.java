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
import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;
import static org.gradle.internal.impldep.org.junit.Assert.assertFalse;
import static org.gradle.internal.impldep.org.junit.Assert.assertNotNull;
import static org.gradle.internal.impldep.org.junit.Assert.assertTrue;
import static org.modelingvalue.gradle.corrector.Util.numOccurences;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.corrector.GitUtil;
import org.modelingvalue.gradle.corrector.Info;
import org.modelingvalue.gradle.corrector.MvgCorrectorPlugin;

public class MvgCorrectorTest {
    private static final String PLUGIN_PACKAGE_NAME = MvgCorrectorPlugin.class.getPackageName();
    private static final String PLUGIN_CLASS_NAME   = MvgCorrectorPlugin.class.getName();
    private static final Path   testWorkspaceDir    = Paths.get("build", "testWorkspace").toAbsolutePath();
    private static final Path   settingsFile        = Paths.get("settings.gradle");
    private static final Path   buildFile           = Paths.get("build.gradle.kts");
    private static final Path   gradlePropsFile     = Paths.get("gradle.properties");
    private static final Path   headFile            = Paths.get(".git", "HEAD");
    private static final Path   javaFile            = Paths.get("src", "main", "java", "A.java");
    private static final Path   propFile            = Paths.get("src", "main", "java", "testCR.properties");
    private static final Path   pruupFile           = Paths.get("src", "main", "java", "testCRLF.pruuperties");

    @Test
    public void checkId() throws IOException {
        Properties props = getProperties();

        assertTrue(props.containsKey("corrector_id"));
        assertEquals(PLUGIN_PACKAGE_NAME, props.get("corrector_id"));

        assertTrue(props.containsKey("corrector_class"));
        assertEquals(PLUGIN_CLASS_NAME, props.get("corrector_class"));

        assertTrue(props.containsKey("corrector_name"));
        assertEquals(Info.CORRECTOR_TASK_NAME, props.get("corrector_name"));
    }

    @Test
    public void checkApplicability() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(PLUGIN_PACKAGE_NAME);

        assertNotNull(project.getTasks().findByName(Info.CORRECTOR_TASK_NAME));
    }

    @Test
    public void checkFunctionality() throws IOException {
        // Setup the test build
        cp(null, settingsFile, javaFile, gradlePropsFile, headFile);
        cp(s -> s.replaceAll("~my-package~", PLUGIN_PACKAGE_NAME).replaceAll("~myExtension~", Info.CORRECTOR_TASK_NAME), buildFile);
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

        // Run the build
        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();
        GradleRunner.create()
                .forwardStdOutput(outWriter)
                .forwardStdError(errWriter)
                .withPluginClasspath()
                .withProjectDir(testWorkspaceDir.toFile())
                .withArguments("--scan", "--info", "--stacktrace", "compileJava")
                .build();
        String out = outWriter.toString();
        String err = errWriter.toString();

        System.out.println("/================================= out ====================================");
        Arrays.stream(out.split("\n")).forEach(l -> System.out.println("| " + l));
        System.out.println("+================================= err ====================================");
        Arrays.stream(err.split("\n")).forEach(l -> System.out.println("| " + l));
        System.out.println("\\==========================================================================");

        GitUtil.untag(testWorkspaceDir, "v0.0.1", "v0.0.2", "v0.0.3", "v0.0.4");

        // Verify the result
        assertEquals(5, numOccurences("+ header regenerated : ", out));
        assertEquals(2, numOccurences("+ eols   regenerated : ", out));
        assertEquals(4, numOccurences("+ eols   untouched   : ", out));
        assertEquals(1, numOccurences("+ found vacant version: 0.0.4 (was 0.0.1)", out));
        assertEquals(1, numOccurences("+ version of project 'testWorkspace' adjusted to from 0.0.1 to 0.0.4", out));
        assertEquals(1, numOccurences("+ dependency     replaced: ", out));
        assertEquals(18, numOccurences("+ dependency NOT replaced: ", out));

        assertTrue(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\nVERSION=0.0.4\n"));
        assertTrue(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright"));
        assertTrue(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright"));
        assertTrue(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright"));
        assertTrue(Files.readString(testWorkspaceDir.resolve(propFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(headFile)).contains("Copyright"));

        assertFalse(Files.readString(testWorkspaceDir.resolve(gradlePropsFile)).contains("\r"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("\r"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("\r"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("\r"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(propFile)).contains("\r"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(pruupFile)).contains("\r"));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static Properties getProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream s = new FileInputStream("../gradle.properties")) {
            props.load(s);
        }
        return props;
    }

    private static void cp(Function<String, String> postProcess, Path... fs) throws IOException {
        for (Path f : fs) {
            String name = f.getFileName().toString();
            try (InputStream in = MvgCorrectorTest.class.getResourceAsStream(name)) {
                try (Scanner scanner = new Scanner(Objects.requireNonNull(in), UTF_8)) {
                    String content = scanner.useDelimiter("\\A").next();
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
