package org.modelingvalue.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

public class GradlePluginsPluginFunctionalTest {
    private final Path srcDir = Paths.get("src");
    private final Path tmpDir = Paths.get("build").resolve("tmp");

    private final Path javaFile     = Paths.get("main", "java", "org", "modelingvalue", "gradle");
    private final Path settingsFile = Paths.get("settings.gradle");
    private final Path buildFile    = Paths.get("build.gradle.kts");

    @Test
    public void canRunTask() throws IOException {
        // Setup the test build

        cp("", settingsFile);
        cp(buildFile);
        cp(javaFile);

        // Run the build
        BuildResult result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("greeting")
                .withProjectDir(tmpDir.toFile())
                .build();
        String output = result.getOutput();

        System.out.println("=============================================");
        Arrays.stream(output.split("\n")).forEach(l -> System.out.println("== " + l));
        System.out.println("=============================================");

        // Verify the result
        Assert.assertTrue(output.contains("Hello from plugin 'org.modelingvalue.gradle.greeting'"));
    }

    private void cp(Path f) throws IOException {
        Path inSrc = srcDir.resolve(f);
        if (Files.isRegularFile(inSrc)) {
            Path inTmp = tmpDir.resolve(f);
            Files.createDirectories(inTmp.getParent());
            Files.copy(inSrc, inTmp);
        } else {
            String name = f.getFileName().toString();
            try (InputStream in = getClass().getResourceAsStream(name)) {
                if (in != null) {
                    try (Scanner scanner = new Scanner(Objects.requireNonNull(getClass().getResourceAsStream(name)), UTF_8)) {
                        cp(scanner.useDelimiter("\\A").next(), f);
                    }
                } else {
                    throw new IOException("cp can not find " + f);
                }
            }
        }
    }

    private void cp(String contents, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.writeString(to, contents);
    }
}
