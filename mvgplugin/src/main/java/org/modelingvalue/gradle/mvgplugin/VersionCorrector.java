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

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_GROUP;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

public class VersionCorrector {
    private final static String        DEFAULT_VERSION = "0.0.1";
    //
    private final        Path          root;
    private final        Project       project;
    private final        String        defaultGroup;
    private final        DotProperties gradleDotProperties;
    private final        Set<Path>     changedFiles    = new HashSet<>();

    public VersionCorrector(MvgCorrectorExtension ext) {
        root = ext.getRoot();
        project = ext.getProject();
        gradleDotProperties = getGradleDotProperties();
        defaultGroup = gradleDotProperties.getFile().getParent().getFileName().toString();
    }

    public Set<Path> getChangedFiles() {
        return changedFiles;
    }

    public VersionCorrector generate() {
        if (!gradleDotProperties.isValid()) {
            LOGGER.info("+ can not determine version: no properties file found at {}", gradleDotProperties.getFile());
        } else {
            String oldVersion = gradleDotProperties.getProp(PROP_NAME_VERSION, DEFAULT_VERSION);
            String group      = gradleDotProperties.getProp(PROP_NAME_GROUP, defaultGroup);
            String newVersion = adjustVersion(oldVersion);

            if (!oldVersion.equals(newVersion)) {
                LOGGER.info("+ overwriting property {} with new version {} (was {}) in property file {}", PROP_NAME_VERSION, newVersion, oldVersion, gradleDotProperties.getFile());
                gradleDotProperties.setProp(PROP_NAME_VERSION, newVersion);
                changedFiles.add(root.relativize(gradleDotProperties.getFile()));
            }

            project.getAllprojects().forEach(p -> {
                if (!Objects.equals(p.getVersion(), newVersion) || !Objects.equals(p.getGroup(), group)) {
                    LOGGER.info("+ project '{}': version: {} => {}, group: {} => {}", p.getName(), p.getVersion(), newVersion, p.getGroup(), group);
                    p.setVersion(newVersion);
                    p.setGroup(group);
                }
            });
        }
        return this;
    }

    private String adjustVersion(String oldVersion) {
        if (!Info.TESTING && !Info.CI) {
            LOGGER.info("+ not on CI (and not TESTING): version not adjusted, version stays {}", oldVersion);
            return oldVersion;
        } else {
            List<String> tags           = GitUtil.listTags(root);
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
                Info.LOGGER.debug("+ ...trying next version: {}", newVersion);
            }
            Info.LOGGER.info("+ found vacant version: {} (was {})", newVersion, oldVersion);
            return newVersion;
        }
    }
}
