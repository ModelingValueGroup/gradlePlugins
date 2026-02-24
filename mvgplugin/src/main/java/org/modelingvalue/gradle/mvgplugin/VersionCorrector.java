//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_GROUP;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMvgCI_orTesting;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

public class VersionCorrector {
    private static final String        DEFAULT_VERSION = "0.0.1";
    //
    private final        Path          root;
    private final        Project       project;
    private final        String        defaultGroup;
    private final        DotProperties gradleDotProperties;

    public VersionCorrector(MvgCorrectorExtension ext) {
        root = ext.getRoot();
        project = ext.getProject();
        gradleDotProperties = getGradleDotProperties();
        defaultGroup = gradleDotProperties.getFile().getParent().getFileName().toString();
    }

    /**
     * Compute the vacant version and set it on all projects.
     * The vacant version is the version from gradle.properties, or the first
     * higher patch version that has no corresponding git tag.
     * This must be called at configuration time so that Gradle's configuration resolution
     * (which freezes artifact file paths) sees the correct version.
     * The gradle.properties file is intentionally not modified — the next
     * vacant version is always derived from git tags.
     */
    public void computeAndSetVersion() {
        if (!gradleDotProperties.isValid()) {
            LOGGER.info("+ mvg: can not determine version: no properties file found at {}", gradleDotProperties.getFile());
            return;
        }
        String propVersion = gradleDotProperties.getProp(PROP_NAME_VERSION, DEFAULT_VERSION);
        String group       = gradleDotProperties.getProp(PROP_NAME_GROUP, defaultGroup);
        String newVersion  = adjustVersion(propVersion);

        project.getAllprojects().forEach(p -> {
            if (!Objects.equals(p.getVersion(), newVersion) || !Objects.equals(p.getGroup(), group)) {
                LOGGER.info("+ mvg: project '{}': version: {} => {}, group: {} => {}", p.getName(), p.getVersion(), newVersion, p.getGroup(), group);
                p.setVersion(newVersion);
                p.setGroup(group);
            }
        });
    }

    private String adjustVersion(String oldVersion) {
        if (!isMvgCI_orTesting()) {
            LOGGER.info("+ mvg: not on CI (and not TESTING): version not adjusted, version stays {}", oldVersion);
            return oldVersion;
        } else {
            List<String> tags           = GitUtil.listTags(root);
            String       versionPattern = "\\d+[.]\\d+[.]\\d+";
            if (!oldVersion.matches(versionPattern)) {
                throw new GradleException("the current version '" + oldVersion + "' does not match the version pattern '" + versionPattern + "'");
            }
            Set<String> versionTags  = tags.stream().filter(t -> t.matches("^[vV]" + versionPattern + "$")).collect(Collectors.toSet());
            String[]    versionParts = oldVersion.split("[.]");
            String      newVersion   = oldVersion;
            while (versionTags.contains("v" + newVersion)) {
                versionParts[versionParts.length - 1] = Integer.toString(Integer.parseInt(versionParts[versionParts.length - 1]) + 1);
                newVersion = String.join(".", versionParts);
                Info.LOGGER.debug("++ mvg: ...trying next version: {}", newVersion);
            }
            Info.LOGGER.info("+ mvg: found vacant version: {} (was {})", newVersion, oldVersion);
            return newVersion;
        }
    }
}
