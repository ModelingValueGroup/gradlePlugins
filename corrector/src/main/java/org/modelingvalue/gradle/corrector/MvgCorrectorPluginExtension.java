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
    public static final String              NAME               = "mvgCorrector";
    //
    private             URL                 headerUrl          = getUrl("https://raw.githubusercontent.com/ModelingValueGroup/generic-info/master/header");
    private             Path                root;
    private             boolean             dry;
    private             boolean             gitpush;
    //
    private final       Set<String>         textFiles          = new HashSet<>(List.of(
            ".gitignore",
            ".gitattributes",
            "LICENSE",
            "header"
    ));
    private final       Set<String>         noTextFiles        = new HashSet<>(List.of(
            ".DS_Store"
    ));
    private final       Set<String>         textExt            = new HashSet<>(List.of(
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
    private final       Set<String>         noTextExt          = new HashSet<>(List.of(
            "class",
            "iml",
            "jar",
            "jar",
            "jpeg",
            "jpg",
            "png"
    ));
    private final       Map<String, String> headerFileExt      = new HashMap<>(Map.of(
            "java", "//",
            "js", "//",
            "kt", "//",
            "kts", "//",
            "gradle", "//",
            "sh", "##",
            "yaml", "##",
            "yml", "##"
    ));
    private final       Set<String>         headerFileExcludes = new HashSet<>(List.of(
            ".git.*",
            ".github/workflows/.*", // github refuses bot pushes of workflows
            ".idea/.*",
            ".gradle/.*",
            "gradle/.*",
            "gradlew.*",
            "MPS/.*",
            ".*/build/.*"
    ));
    private final       Set<String>         eolFileExcludes    = new HashSet<>(List.of(
            ".git.*",
            ".github/workflows/.*", // github refuses bot pushes of workflows
            ".idea/.*",
            ".gradle/.*",
            "gradle/.*",
            "gradlew.*",
            "MPS/.*",
            ".*/build/.*"
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

    public void setGitPush(boolean gitpush) {
        this.gitpush = gitpush;
    }

    public boolean getGitPush() {
        return gitpush;
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

    public void addTextFileExtension(String pattern) {
        textExt.add(pattern);
    }

    public Set<String> getTextFileExtensions() {
        return textExt;
    }

    public void addNoTextFileExtension(String pattern) {
        noTextExt.add(pattern);
    }

    public Set<String> getNoTextFileExtensions() {
        return noTextExt;
    }

    public void addHeaderFileExtension(String ext, String comment) {
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

    public void addEolFileExclude(String pattern) {
        eolFileExcludes.add(pattern);
    }

    public Set<String> getEolFileExcludes() {
        return eolFileExcludes;
    }

    private static URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new Error("not a valid url in header: " + url, e);
        }
    }
}
