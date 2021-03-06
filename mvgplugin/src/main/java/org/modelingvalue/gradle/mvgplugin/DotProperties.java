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

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class DotProperties {
    private final DotProperties parent;
    private final File          file;
    private final boolean       valid;
    private final Properties    properties;
    private final List<String>  lines;

    public DotProperties(DotProperties parent, File file) {
        this.parent = parent;
        this.file = file;
        valid = file.isFile();
        properties = valid ? Util.loadProperties(file) : new Properties();
        if (valid) {
            try {
                lines = Files.readAllLines(file.toPath());
            } catch (IOException e) {
                throw new GradleException("properties file could not be read: " + file.getAbsolutePath(), e);
            }
        } else {
            lines = new ArrayList<>();
        }
    }

    public DotProperties(File file) {
        this(null, file);
    }

    public File getFile() {
        return file;
    }

    public boolean isValid() {
        return valid;
    }

    public String getProp(String name) {
        return getProp(name, null);
    }

    public String getProp(String name, String def) {
        Object o = properties.get(name);
        String value = o != null ? o.toString() : parent != null ? parent.getProp(name, def) : def;
        LOGGER.info("+~ getProp          : {} => {}   (from {}{})", name, Util.hide(value), file.getAbsolutePath(), valid ? "" : " - INVALID");
        return value;
    }


    public void setProp(String name, String val) {
        if (valid) {
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
}
