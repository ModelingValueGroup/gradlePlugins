package org.modelingvalue.gradle.corrector;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;

@SuppressWarnings({"FieldCanBeLocal"})
public class MvgCorrectorPluginExtension {

    private       URL                 headerUrl          = getUrl("https://raw.githubusercontent.com/ModelingValueGroup/generic-info/master/header");
    private       Path                root;
    private       boolean             dry;
    //
    private final Set<String>         textFiles          = new HashSet<>(List.of(
            ".gitignore",
            ".gitattributes",
            "LICENSE",
            "header"
    ));
    private final Set<String>         noTextFiles        = new HashSet<>(List.of(
            ".DS_Store"
    ));
    private final Set<String>         textExt            = new HashSet<>(List.of(
            "MF",
            "java",
            "js",
            "md",
            "pom",
            "properties",
            "sh",
            "txt",
            "xml",
            "yaml",
            "yml",
            "adoc",
            "project",
            "prefs",
            "classpath",
            "jardesc",
            "mps",
            "mpl",
            "msd",
            "kt",
            "kts",
            "gradle"
    ));
    private final Set<String>         noTextExt          = new HashSet<>(List.of(
            "class",
            "iml",
            "jar",
            "jar",
            "jpeg",
            "jpg",
            "png"
    ));
    private final Map<String, String> headerFileExt      = new HashMap<>(Map.of(
            "java", "//",
            "js", "//",
            "kt", "//",
            "kts", "//",
            "gradle", "//",
            "sh", "##",
            "yaml", "##",
            "yml", "##"
    ));
    private final Set<String>         headerFileExcludes = new HashSet<>(List.of(
            "MPS/.*",
            ".git.*",
            ".idea/.*",
            ".*/.gradle/.*",
            ".*/gradle/.*",
            ".*/out/.*",
            ".*/build/.*",
            ".*/lib/.*",
            ".*/gradlew.*",
            ".github/workflows/.*" // github refuses bot pushes of workflows
    ));

    public MvgCorrectorPluginExtension(Project project) {
        this.root = project.getRootDir().toPath();
    }

    public void setHeaderUrl(String url) {
        headerUrl = getUrl(url);
    }

    public URL getHeaderUrl() {
        return headerUrl;
    }

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public void setDry(boolean dry) {
        this.dry = dry;
    }

    public boolean getDry() {
        return dry;
    }

    public void addTextFile(String pattern) {
        textFiles.add(pattern);
    }

    public Set<String> getTextFiles() {
        return textFiles;
    }

    public void addNoTextFile(String pattern) {
        noTextFiles.add(pattern);
    }

    public Set<String> getNoTextFiles() {
        return noTextFiles;
    }

    public void addTextFileExtention(String pattern) {
        textExt.add(pattern);
    }

    public Set<String> getTextFileExtentions() {
        return textExt;
    }

    public void addNoTextFileExtention(String pattern) {
        noTextExt.add(pattern);
    }

    public Set<String> getNoTextFileExtentions() {
        return noTextExt;
    }

    public void addHeaderFileExtention(String ext, String comment) {
        if (comment == null) {
            headerFileExt.remove(ext);
        } else {
            headerFileExt.put(ext, comment);
        }
    }

    public Map<String, String> getHeaderFileExtensions() {
        return headerFileExt;
    }

    public void addHeaderFileExclude(String pattern) {
        headerFileExcludes.add(pattern);
    }

    public Set<String> getHeaderFileExcludes() {
        return headerFileExcludes;
    }

    private static URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new Error("not a valid url in header: " + url, e);
        }
    }
}
