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

package org.modelingvalue.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;
import static org.gradle.internal.impldep.org.junit.Assert.assertFalse;
import static org.gradle.internal.impldep.org.junit.Assert.assertNotNull;
import static org.gradle.internal.impldep.org.junit.Assert.assertThrows;
import static org.gradle.internal.impldep.org.junit.Assert.assertTrue;
import static org.modelingvalue.gradle.corrector.Util.numOccurences;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.corrector.Info;
import org.modelingvalue.gradle.corrector.MvgCorrectorPlugin;

public class MvgCorrectorTest {
    private static final String PLUGIN_PACKAGE_NAME = MvgCorrectorPlugin.class.getPackageName();
    private static final String PLUGIN_CLASS_NAME   = MvgCorrectorPlugin.class.getName();
    private static final Path   testWorkspaceDir    = Paths.get("build").resolve("testWorkspace");
    private static final Path   settingsFile        = Paths.get("settings.gradle");
    private static final Path   buildFile           = Paths.get("build.gradle.kts");
    private static final Path   gradlePropsFile     = Paths.get("gradle.properties");
    private static final Path   javaFile            = Paths.get("main", "java", "A.java");
    private static final Path   prop1File           = Paths.get("main", "java", "testCR.properties");
    private static final Path   pruup2File          = Paths.get("main", "java", "testCRLF.pruuperties");

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
        System.setProperty("CI", "true");
        System.setProperty("TOKEN", "DRY");

        // Setup the test build
        cp(null, settingsFile, javaFile, gradlePropsFile);
        cp(s -> s.replaceAll("<my-package>", PLUGIN_PACKAGE_NAME).replaceAll("<myExtension>", Info.CORRECTOR_TASK_NAME), buildFile);
        cp(s -> s.replaceAll("\n", "\r"), prop1File);
        cp(s -> s.replaceAll("\n", "\n\r"), pruup2File);

        // Run the build
        StringWriter outWriter = new StringWriter();
        StringWriter errWriter = new StringWriter();
        assertThrows(UnexpectedBuildFailure.class, () -> GradleRunner.create()
                .forwardStdOutput(outWriter)
                .forwardStdError(errWriter)
                .withPluginClasspath()
                .withProjectDir(testWorkspaceDir.toFile())
                .withArguments("--info", Info.CORRECTOR_TASK_NAME, "compileJava", Info.TAG_TASK_NAME)
                .build());
        String out = outWriter.toString();
        String err = errWriter.toString();

        System.out.println("================================== out ====================================");
        System.out.println(out);
        System.out.println("================================== err ====================================");
        System.out.println(err);
        System.out.println("===========================================================================");

        // Verify the result
        assertEquals(3, numOccurences("+ header regenerated : ", out));
        assertEquals(2, numOccurences("+ eols   regenerated : ", out));
        assertEquals(4, numOccurences("+ eols   untouched   : ", out));
        assertEquals(1, numOccurences("abandoned build run", err));
        assertEquals(1, numOccurences("+ version updated from 0.0.1 to 0.0.3", out));

        assertTrue(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(settingsFile)).contains("\r"));

        assertTrue(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(buildFile)).contains("\r"));

        assertTrue(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(javaFile)).contains("\r"));

        assertFalse(Files.readString(testWorkspaceDir.resolve(prop1File)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(prop1File)).contains("\r"));

        assertFalse(Files.readString(testWorkspaceDir.resolve(pruup2File)).contains("Copyright"));
        assertFalse(Files.readString(testWorkspaceDir.resolve(pruup2File)).contains("\r"));
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
