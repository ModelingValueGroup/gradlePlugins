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

package org.modelingvalue.gradle.mvgplugin;

import static org.modelingvalue.gradle.mvgplugin.Info.CORRECTOR_TASK_NAME;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.extensibility.DefaultConvention;

public class MvgCorrectorExtension {
    public static MvgCorrectorExtension make(Gradle gradle) {
        Project project = gradle.getRootProject();
        return ((DefaultConvention) project.getExtensions()).create(CORRECTOR_TASK_NAME, MvgCorrectorExtension.class, project);
    }

    private final Project             project;
    private       URL                 headerUrl;
    private final Set<String>         textFiles;
    private final Set<String>         noTextFiles;
    private final Set<String>         textExt;
    private final Set<String>         noTextExt;
    private final Map<String, String> headerFileExt;
    private final Set<String>         headerFileExcludes;
    private final Set<String>         eolFileExcludes;
    private final Set<String>         bashFileExcludes;
    public        boolean             forceEolCorrection;
    public        boolean             forceHeaderCorrection;
    public        boolean             forceDependabotCorrection;
    public        boolean             forceBashCorrection;
    public        boolean             forceVersionCorrection;

    public MvgCorrectorExtension(Project project) {
        this.project = project;
        headerUrl = Util.getUrl("https://raw.githubusercontent.com/ModelingValueGroup/generic-info/master/header");
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
        List<String> defaultExcludes = List.of(
                ".git/.*",
                ".github/workflows/.*", // github refuses bot pushes of workflows
                ".idea/.*",
                ".gradle/.*",
                "gradle/.*",
                "gradlew.*",
                "MPS/.*",
                ".*/build/.*",
                ".*/[^/]*_gen/.*" // MPS type generation directories
        );
        headerFileExcludes = new HashSet<>(defaultExcludes);
        eolFileExcludes = new HashSet<>(defaultExcludes);
        bashFileExcludes = new HashSet<>(defaultExcludes);
    }

    public Project getProject() {
        return project;
    }

    public Path getRoot() {
        return project.getRootDir().toPath();
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

    public void addBashFileExclude(String pattern) {
        bashFileExcludes.add(pattern);
    }

    public Set<String> getBashFileExcludes() {
        return bashFileExcludes;
    }
}
