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

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultConvention;

@SuppressWarnings({"FieldCanBeLocal"})
public class CorrectorExtension {
    public static CorrectorExtension make(Project project, String name) {
        return ((DefaultConvention) project.getExtensions()).create(name, CorrectorExtension.class, project);
    }

    private final Project             project;
    private       URL                 headerUrl;
    private       Path                propFileWithVersion;
    private       String              versionName;
    private final Set<String>         textFiles;
    private final Set<String>         noTextFiles;
    private final Set<String>         textExt;
    private final Set<String>         noTextExt;
    private final Map<String, String> headerFileExt;
    private final Set<String>         headerFileExcludes;
    private final Set<String>         eolFileExcludes;

    public CorrectorExtension(Project project) {
        this.project = project;
        headerUrl = Util.getUrl("https://raw.githubusercontent.com/ModelingValueGroup/generic-info/master/header");
        propFileWithVersion = project.getRootProject().getRootDir().toPath().resolve("gradle.properties");
        versionName = "VERSION";
        textFiles = new HashSet<>(List.of(
                ".gitignore",
                ".gitattributes",
                "LICENSE",
                "header"
        ));
        noTextFiles = new HashSet<>(List.of(
                ".DS_Store"
        ));
        textExt = new HashSet<>(List.of(
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
        noTextExt = new HashSet<>(List.of(
                "class",
                "iml",
                "jar",
                "jar",
                "jpeg",
                "jpg",
                "png"
        ));
        headerFileExt = new HashMap<>(Map.of(
                "java", "//",
                "js", "//",
                "kt", "//",
                "kts", "//",
                "gradle", "//",
                "properties", "##",
                "sh", "##",
                "yaml", "##",
                "yml", "##"
        ));
        headerFileExcludes = new HashSet<>(List.of(
                ".git.*",
                ".github/workflows/.*", // github refuses bot pushes of workflows
                ".idea/.*",
                ".gradle/.*",
                "gradle/.*",
                "gradlew.*",
                "MPS/.*",
                ".*/build/.*"
        ));
        eolFileExcludes = new HashSet<>(List.of(
                ".git.*",
                ".github/workflows/.*", // github refuses bot pushes of workflows
                ".idea/.*",
                ".gradle/.*",
                "gradle/.*",
                "gradlew.*",
                "MPS/.*",
                ".*/build/.*"
        ));
    }

    public Project getProject() {
        return project;
    }

    public Path getRoot() {
        return project.getRootDir().toPath();
    }

    public String getProjectVersion() {
        return project.getVersion().toString();
    }

    public void setHeaderUrl(String url) {
        headerUrl = Util.getUrl(url);
    }

    public URL getHeaderUrl() {
        return headerUrl;
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

    public void setPropFileWithVersion(Path propFileWithVersion) {
        this.propFileWithVersion = propFileWithVersion;
    }

    public Path getPropFileWithVersion() {
        return propFileWithVersion;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getVersionName() {
        return versionName;
    }
}
