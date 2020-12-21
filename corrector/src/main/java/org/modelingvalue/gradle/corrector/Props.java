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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class Props {
    private List<String> lines;

    public Props(Path f) {
        if (!Files.isReadable(f)) {
            throw new GradleException("properties file not found: " + f);
        }
        try {
            lines = Files.readAllLines(f);
        } catch (IOException e) {
            throw new GradleException("properties file could not be read: " + f, e);
        }
    }

    public List<String> getLines() {
        return lines;
    }

    public String getProp(String name, String def) {
        return lines.stream()
                .filter(l -> l.matches("^" + Pattern.quote(name) + "=.*"))
                .map(l -> l.replaceAll("^[^=]*=", ""))
                .findFirst()
                .orElse(def);
    }

    public void setProp(String name, String val) {
        lines = lines.stream()
                .map(l -> l.matches("^" + Pattern.quote(name) + "=.*") ? name + "=" + val : l)
                .collect(Collectors.toList());
    }
}
