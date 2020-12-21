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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

public class VerCorrector extends CorrectorBase {
    private final Project project;
    private final Path    propFile;
    private final String  propName;
    private final String  projectVersion;
    private final Path    absPropFile;

    public VerCorrector(CorrectorExtension ext) {
        super("vers  ", ext.getRoot(), ext.getEolFileExcludes());
        project = ext.getProject();
        propFile = ext.getPropFileWithVersion();
        propName = ext.getVersionName();
        projectVersion = ext.getProjectVersion();
        absPropFile = getAbsPropFile(propFile);
    }

    private Path getAbsPropFile(Path propFile) {
        Path f = propFile;
        if (f != null && !f.isAbsolute()) {
            f = getRoot().resolve(propFile);
        }
        return f;
    }

    public VerCorrector generate() {
        if (propFile == null) {
            LOGGER.info("+ can not find a a proper version: no properties file specified");
        } else {
            Props  props      = new Props(propFile);
            String oldVersion = props.getProp(propName, "0.0.1");
            String newVersion = findVacantVersion(oldVersion);
            if (!oldVersion.equals(newVersion)) {
                props.setProp(propName, newVersion);
                overwrite(absPropFile, props.getLines());
            }
            project.getAllprojects().forEach(p -> {
                if (p.getVersion().equals(oldVersion)) {
                    LOGGER.info("+ version of project '{}' adjusted to from {} to {}", p.getName(), oldVersion, newVersion);
                    p.setVersion(newVersion);
                } else {
                    LOGGER.info("+ version of project '{}' NOT adjusted to {}, because it is not {} but {}", p.getName(), newVersion, oldVersion, p.getVersion());
                }
            });
        }
        return this;
    }

    private String findVacantVersion(String oldVersion) {
        List<String> tags           = GitUtil.getAllTags(getRoot());
        String       versionPattern = "\\d\\d*[.]\\d\\d*[.]\\d\\d*";
        if (!oldVersion.matches(versionPattern)) {
            throw new GradleException("the current version '" + oldVersion + "' does not match the version pattern '" + versionPattern + "'");
        }
        Set<String> versionTags  = tags.stream().filter(t -> t.matches("^[vV]" + versionPattern + "$")).collect(Collectors.toSet());
        String[]    versionParts = oldVersion.split("[.]");
        String      newVersion   = oldVersion;
        while (versionTags.contains("v" + newVersion)) {
            versionParts[versionParts.length - 1] = Integer.toString(Integer.parseInt(versionParts[versionParts.length - 1]) + 1);
            newVersion = String.join(".", versionParts);
            Info.LOGGER.trace("+ ...trying next version: {}", newVersion);
        }
        Info.LOGGER.info("+ found vacant version: {} (was {})", newVersion, oldVersion);
        return newVersion;
    }
}
