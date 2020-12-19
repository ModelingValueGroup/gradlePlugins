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

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.LOGGER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class VerCorrector extends CorrectorBase {
    private final Path   propFile;
    private final String propName;
    private final String projectVersion;
    private final Path   absPropFile;

    private Path getAbsPropFile(Path propFile) {
        Path f = propFile;
        if (f != null && !f.isAbsolute()) {
            f = getRoot().resolve(propFile);
        }
        return f;
    }

    public VerCorrector(CorrectorExtension ext) {
        super("vers  ", ext.getRoot(), ext.getEolFileExcludes(), ext.getDry());
        propFile = ext.getFileWithVersion();
        propName = ext.getVersionName();
        projectVersion = ext.getProjectVersion();
        absPropFile = getAbsPropFile(propFile);
    }

    public VerCorrector generate() throws IOException {
        if (propFile != null) {
            List<String> lines      = getPropertyLines(absPropFile);
            int          index      = getPropertyIndex(lines);
            String       oldVersion = getPropValue(lines, index);
            String       newVersion = findVacantVersion(oldVersion);
            if (!oldVersion.equals(newVersion)) {
                lines.set(index, propName + "=" + newVersion);
                overwrite(absPropFile, lines);
                Info.LOGGER.info("+ version updated from " + oldVersion + " to " + newVersion);
            }
            if (!projectVersion.equals(oldVersion)) {
                LOGGER.error("the project's version '{}' is different from the version from the properties file '{}'", projectVersion, oldVersion);
            }
        }
        return this;
    }

    private List<String> getPropertyLines(Path f) throws IOException {
        if (!Files.isReadable(f)) {
            throw new GradleException("properties file with version not found: " + f);
        }
        return Files.readAllLines(f);
    }

    private int getPropertyIndex(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).matches("^" + Pattern.quote(propName) + "=.*")) {
                return i;
            }
        }
        throw new GradleException("property '" + propName + "' not found in: " + absPropFile);
    }

    private String getPropValue(List<String> lines, int index) {
        return lines.get(index).replaceAll("[^=]*=", "");
    }

    private String findVacantVersion(String v) {
        List<String> tags           = GitUtil.getAllTags(getRoot());
        String       versionPattern = "\\d\\d*[.]\\d\\d*[.]\\d\\d*";
        if (!v.matches(versionPattern)) {
            throw new GradleException("the current version '" + v + "' does not match the version pattern '" + versionPattern + "'");
        }
        Set<String> versionTags  = tags.stream().filter(t -> t.matches("^[vV]" + versionPattern + "$")).collect(Collectors.toSet());
        String[]    versionParts = v.split("[.]");
        while (versionTags.contains("v" + v)) {
            versionParts[versionParts.length - 1] = Integer.toString(Integer.parseInt(versionParts[versionParts.length - 1]) + 1);
            v = String.join(".", versionParts);
            Info.LOGGER.trace("+ ...trying next version: {}", v);
        }
        Info.LOGGER.info("+ found vacant version version: {}", v);
        return v;
    }
}
