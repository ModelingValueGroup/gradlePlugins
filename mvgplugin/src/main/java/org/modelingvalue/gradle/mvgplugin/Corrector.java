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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Corrector {
    protected final String    name;
    protected final String    nameField;
    protected final Set<Path> changedFiles = new HashSet<>();

    public Corrector(String name) {
        this.name = name;
        nameField = String.format("%-10s", name);
    }

    protected void overwrite(Path file, List<String> lines) {
        try {
            if (!Files.isRegularFile(file)) {
                LOGGER.info("+ mvg: {} generated   : {}", nameField, file);
                Files.write(file, lines);
                changedFiles.add(file);
            } else {
                String was = Files.readString(file);
                String req = String.join("\n", lines);
                if (was.endsWith("\n") || was.endsWith("\r")) {
                    req += "\n";
                }
                if (!req.equals(was)) {
                    LOGGER.info("+ mvg: {} regenerated : {}", nameField, file);
                    LOGGER.debug("++ mvg: ====\n" + was.replaceAll("\r", "•") + "====\n" + req + "====\n");
                    Files.write(file, req.getBytes());
                    changedFiles.add(file);
                } else {
                    LOGGER.info("+ mvg: {} untouched   : {}", nameField, file);
                }
            }
        } catch (IOException e) {
            throw new Error("could not overwrite file for " + name + " : " + file, e);
        }
    }

    public Set<Path> getChangedFiles(Path rel) {
        return changedFiles.stream().map(rel::relativize).collect(Collectors.toSet());
    }
}
