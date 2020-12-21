package org.modelingvalue.gradle.corrector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class Props {
    private List<String> lines;

    public Props(Path f) {
        if (!Files.isReadable(f)) {
            throw new GradleException("properties file not found: " + f.toAbsolutePath());
        }
        try {
            lines = Files.readAllLines(f);
        } catch (IOException e) {
            throw new GradleException("properties file could not be read: " + f.toAbsolutePath(), e);
        }
    }

    public List<String> getLines() {
        return lines;
    }

    public String getProp(String name, String def) {
        return lines.stream()
                .filter(l -> l.matches("^" + Pattern.quote(name) + "=.*"))
                .map(l -> l.replaceAll("^[^=]*=", ""))
                .findFirst()
                .orElse(def);
    }

    public void setProp(String name, String val) {
        lines = lines.stream()
                .map(l -> l.matches("^" + Pattern.quote(name) + "=.*") ? name + "=" + val : l)
                .collect(Collectors.toList());
    }
}
