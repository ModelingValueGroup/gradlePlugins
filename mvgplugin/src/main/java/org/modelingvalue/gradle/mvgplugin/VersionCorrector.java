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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

public class VersionCorrector {
    private static final String        DEFAULT_VERSION = "0.0.1";
    public static final  String        DEV_VERSION     = "dev";
    private static final String        VERSION_PATTERN = "\\d+[.]\\d+[.]\\d+";
    // a version-like tag is a 3-part version with an arbitrary non-digit prefix: v1.2.3, V1.2.3, release1.2.3, 1.2.3
    private static final Pattern       VERSION_TAG     = Pattern.compile("^\\D*(" + VERSION_PATTERN + ")$");
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
     * On CI the vacant version is the patch successor of the highest version-like git tag,
     * or the gradle.properties version itself when that is higher than every tag (the way
     * to deliberately start a new minor/major release line).
     * Outside CI the version is always "dev": local artifacts are not for distribution and
     * must not look like a release.
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
            LOGGER.info("+ mvg: not on CI (and not TESTING): version set to '{}' (local artifacts are not for distribution)", DEV_VERSION);
            return DEV_VERSION;
        } else {
            if (!oldVersion.matches(VERSION_PATTERN)) {
                throw new GradleException("the current version '" + oldVersion + "' does not match the version pattern '" + VERSION_PATTERN + "'");
            }
            String newVersion = vacantVersion(oldVersion, GitUtil.listTags(root));
            Info.LOGGER.info("+ mvg: found vacant version: {} (was {})", newVersion, oldVersion);
            return newVersion;
        }
    }

    /**
     * The vacant version is the patch successor of the highest version-like tag, or the
     * gradle.properties version itself when that is higher than every tag (or when there
     * are no version-like tags at all).
     */
    public static String vacantVersion(String propVersion, List<String> tags) {
        Version highestTag = tags.stream()
                .map(VERSION_TAG::matcher)
                .filter(Matcher::matches)
                .map(m -> new Version(m.group(1)))
                .max(Comparator.naturalOrder())
                .orElse(null);
        Version prop = new Version(propVersion);
        if (highestTag == null || prop.compareTo(highestTag) > 0) {
            return prop.get();
        }
        return nextPatch(highestTag);
    }

    private static String nextPatch(Version version) {
        String[] parts = version.get().split("[.]");
        parts[parts.length - 1] = Integer.toString(Integer.parseInt(parts[parts.length - 1]) + 1);
        return String.join(".", parts);
    }
}
