package org.modelingvalue.gradle;


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;
import static org.gradle.internal.impldep.org.junit.Assert.assertFalse;
import static org.gradle.internal.impldep.org.junit.Assert.assertNotNull;
import static org.gradle.internal.impldep.org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.corrector.Info;
import org.modelingvalue.gradle.corrector.MvgCorrectorPlugin;

public class MvgCorrectorTest {
    private static final Path testWorkspaceDir = Paths.get("build").resolve("testWorkspace");
    private static final Path settingsFile     = Paths.get("settings.gradle");
    private static final Path buildFile        = Paths.get("build.gradle.kts");
    private static final Path javaFile         = Paths.get("main", "java", "A.java");
    private static final Path prop1File        = Paths.get("main", "java", "testCR.properties");
    private static final Path pruup2File       = Paths.get("main", "java", "testCRLF.pruuperties");

    @Test
    public void checkId() throws IOException {
        String idName    = MvgCorrectorPlugin.class.getPackageName();
        String idPattern = "val *correctorId *= *\"" + Pattern.quote(idName) + "\" *//.*";
        assertTrue("the plugin " + idName + " is not used as id in " + buildFile, Files.readAllLines(buildFile).stream().anyMatch(l -> l.matches(idPattern)));

        String className    = MvgCorrectorPlugin.class.getName();
        String classPattern = "val *correctorClass *= *\"" + Pattern.quote(className) + "\" *//.*";
        assertTrue("the plugin classname " + className + " is not used as implementationClass in " + buildFile, Files.readAllLines(buildFile).stream().anyMatch(l -> l.matches(classPattern)));

        String pluginName    = Info.EXTENSION_NAME;
        String pluginPattern = "val *correctorName *= *\"" + Pattern.quote(pluginName) + "\" *//.*";
        assertTrue("the plugin name " + pluginName + " is not used as correctorName in " + buildFile, Files.readAllLines(buildFile).stream().anyMatch(l -> l.matches(pluginPattern)));
    }

    @Test
    public void checkApplicability() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(Info.class.getPackageName());

        assertNotNull(project.getTasks().findByName(Info.EXTENSION_NAME));
    }

    @Test
    public void checkFunctionality() throws IOException {
        // Setup the test build
        cp(null, settingsFile, javaFile);
        cp(s -> s.replaceAll("<my-package>", Info.class.getPackageName()).replaceAll("<myExtension>", Info.EXTENSION_NAME), buildFile);
        cp(s -> s.replaceAll("\n", "\r"), prop1File);
        cp(s -> s.replaceAll("\n", "\n\r"), pruup2File);

        // Run the build
        BuildResult result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("--info", Info.EXTENSION_NAME)
                .withProjectDir(testWorkspaceDir.toFile())
                .build();
        String output = result.getOutput();

        // Verify the result
        assertEquals(3, numOccurences("+ header   regenerated : ", output));
        assertEquals(2, numOccurences("+ eols     regenerated : ", output));
        assertEquals(3, numOccurences("+ eols     untouched   : ", output));

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
