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
import static org.gradle.internal.impldep.org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.corrector.MvgCorrectorPlugin;
import org.modelingvalue.gradle.corrector.MvgCorrectorPluginExtension;

public class MvgCorrectorTest {
    private static final String PLUGIN_PACKAGE_NAME = MvgCorrectorPlugin.class.getPackageName();
    private static final String PLUGIN_CLASS_NAME   = MvgCorrectorPlugin.class.getName();
    private static final Path   testWorkspaceDir    = Paths.get("build").resolve("testWorkspace");
    private static final Path   settingsFile        = Paths.get("settings.gradle");
    private static final Path   buildFile           = Paths.get("build.gradle.kts");
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
        assertEquals(MvgCorrectorPluginExtension.NAME, props.get("corrector_name"));
    }

    @Test
    public void checkApplicability() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(PLUGIN_PACKAGE_NAME);

        assertNotNull(project.getTasks().findByName(MvgCorrectorPluginExtension.NAME));
    }

    @Test
    public void checkFunctionality() throws IOException {
        // Setup the test build
        cp(null, settingsFile, javaFile);
        cp(s -> s.replaceAll("<my-package>", PLUGIN_PACKAGE_NAME).replaceAll("<myExtension>", MvgCorrectorPluginExtension.NAME), buildFile);
        cp(s -> s.replaceAll("\n", "\r"), prop1File);
        cp(s -> s.replaceAll("\n", "\n\r"), pruup2File);

        // Run the build
        BuildResult result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("--info", MvgCorrectorPluginExtension.NAME)
                .withProjectDir(testWorkspaceDir.toFile())
                .build();
        String output = result.getOutput();

        // Verify the result
        assertEquals(3, numOccurences("+ header regenerated : ", output));
        assertEquals(2, numOccurences("+ eols   regenerated : ", output));
        assertEquals(3, numOccurences("+ eols   untouched   : ", output));

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

    private int numOccurences(String find, String all) {
        int count     = 0;
        int fromIndex = 0;
        while ((fromIndex = all.indexOf(find, fromIndex)) != -1) {
            count++;
            fromIndex++;
        }
        return count;
    }
}
