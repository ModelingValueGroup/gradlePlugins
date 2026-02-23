//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class DotProperties {
    private final DotProperties parent;
    private final Path          file;
    private       String        crc;
    private final boolean       valid;
    private final Properties    properties;
    private final List<String>  lines;

    public DotProperties(Path file, DotProperties parent) {
        this.parent = parent;
        this.file = file;
        valid = Files.isRegularFile(file);
        if (valid) {
            crc = Hash.MD5.checksum(file);
            properties = Util.loadProperties(file);
            LOGGER.debug("++ mvg: properties from {}: read {} properties", file, properties.size());
            properties.forEach((name, value) -> LOGGER.debug("++ mvg: prop read: [{}] = {}", name, value == null ? null : Util.hide(value.toString())));
            try {
                lines = Files.readAllLines(file);
            } catch (IOException e) {
                throw new GradleException("properties file could not be read: " + file.toAbsolutePath(), e);
            }
        } else {
            crc = "";
            properties = new Properties();
            lines = new ArrayList<>();
            LOGGER.debug("++ mvg: properties file {}: no file, no values", file);
        }
    }

    public DotProperties(Path file) {
        this(file, null);
    }

    public Path getFile() {
        return file;
    }

    public boolean isValid() {
        return valid;
    }

    public String getProp(String name) {
        return getProp(name, null);
    }

    public String getProp(String name, String def) {
        trackFileChange();
        Object o = properties.get(name);
        LOGGER.debug("++ mvg: getProp raw get on {}: [{}] = {}", file, name, o == null ? o : Util.hide(o.toString()));
        String value = o != null ? o.toString() : parent != null ? parent.getProp(name, def) : def;
        LOGGER.info("+ mvg: getProp          : {} => {}   (from {}{})", name, Util.hide(value), file.toAbsolutePath(), valid ? "" : " - INVALID");
        return value;
    }

    private void trackFileChange() {
        String newCrc = Hash.MD5.checksum(file);
        if (!newCrc.equals(crc)) {
            Properties newProps = Util.loadProperties(file);
            if (newProps.equals(properties)) {
                LOGGER.info("+ mvg: FILE CHANGED: {} ({}!={}) but same properties so updating crc", file, crc, newCrc);
                crc = newCrc;
            } else {
                LOGGER.info("+ mvg: FILE CHANGED: {} ({}!={})", file, crc, newCrc);
                throw new GradleException("consistency problem: the file " + file + " has changed in mid air ('gradle --stop' might solve this)");
            }
        }
    }

    public void setProp(String name, String value) {
        LOGGER.debug("++ mvg: setProp on {}: [{}] = {}", file, name, Util.hide(value));
        trackFileChange();
        properties.setProperty(name, value);
        if (valid) {
            List<String> newLines = lines.stream()
                    .map(l -> l.matches("^" + Pattern.quote(name) + "\\s*=.*") ? l.replaceFirst("(=\\s*).*$", "$1") + value : l)
                    .collect(Collectors.toList());
            lines.clear();
            lines.addAll(newLines);
            try {
                Files.write(file, lines);
                crc = Hash.MD5.checksum(file);
            } catch (IOException e) {
                throw new GradleException("properties file could not be written: " + file.toAbsolutePath(), e);
            }
        }
    }
}
