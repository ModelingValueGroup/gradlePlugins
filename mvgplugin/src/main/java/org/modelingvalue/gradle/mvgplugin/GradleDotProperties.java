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

import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;

public class GradleDotProperties {
    private static GradleDotProperties instance;

    public static void init(Gradle gradle) {
        init(gradle.getRootProject().getRootDir());
    }

    public static void init(File dir) { // separated out for testing purposes
        if (instance != null && !instance.file.getParentFile().equals(dir)) {
            LOGGER.warn("{} file switched in mid air: from {} to {}", GRADLE_PROPERTIES_FILE, instance.file.getAbsolutePath(), new File(dir, GRADLE_PROPERTIES_FILE).getAbsolutePath());
        }
        instance = new GradleDotProperties(dir);
    }

    public static GradleDotProperties getGradleDotProperties() {
        if (instance == null) {
            LOGGER.error("GradleDotProperties not yet initialized");
            throw new GradleException("GradleDotProperties not yet initialized");
        }
        return instance;
    }

    private final File         file;
    private final boolean      valid;
    private final Properties   properties;
    private final List<String> lines;

    private GradleDotProperties(File dir) {
        file = new File(dir, GRADLE_PROPERTIES_FILE);
        valid = file.isFile();
        properties = valid ? Util.loadProperties(file) : new Properties();
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new GradleException("properties file could not be read: " + file.getAbsolutePath(), e);
        }
    }

    public File getFile() {
        return file;
    }

    public boolean isValid() {
        return valid;
    }

    public String getProp(String name, String def) {
        Object o = properties.get(name);
        String v = o == null ? def : o.toString();
        LOGGER.info("+ reading property {} from the {}property file {} as {}", name, valid ? "" : "INVALID ", file.getAbsolutePath(), v);
        return v;
    }


    public void setProp(String name, String val) {
        List<String> newLines = lines.stream()
                .map(l -> l.matches("^" + Pattern.quote(name) + "=.*") ? name + "=" + val : l)
                .collect(Collectors.toList());
        lines.clear();
        lines.addAll(newLines);
        try {
            Files.write(file.toPath(), lines);
        } catch (IOException e) {
            throw new GradleException("properties file could not be written: " + file.getAbsolutePath(), e);
        }
    }
}
