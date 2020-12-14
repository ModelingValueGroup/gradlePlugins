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
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

@SuppressWarnings({"WeakerAccess"})
public abstract class CorrectorBase {
    private static final Logger      LOGGER = Logging.getLogger(Info.EXTENSION_NAME);
    //
    private final        String      name;
    private final        Path        root;
    private final        Set<String> excludes;
    private final        boolean     dry;

    public CorrectorBase(String name, Path root, Set<String> excludes, boolean dry) {
        this.name = name;
        this.root = root;
        this.excludes = excludes;
        this.dry = dry;
    }

    Stream<Path> allFiles() throws IOException {
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> excludes.stream().noneMatch(pattern -> Paths.get(".").resolve(root.relativize(p)).toString().matches(pattern)));
    }

    void overwrite(Path file, List<String> lines) {
        try {
            if (!Files.isRegularFile(file)) {
                LOGGER.info("+ {} generated   : {}", String.format("%-8s", name), file);
                if (!dry) {
                    Files.write(file, lines);
                }
            } else {
                String was = Files.readString(file);
                String req = String.join("\n", lines);
                if (0 < was.length() && was.endsWith("\n")) {
                    req += "\n";
                }
                if (!req.equals(was)) {
                    LOGGER.info("+ {} regenerated : {}", String.format("%-8s", name), file);
                    LOGGER.trace("====\n" + was.replaceAll("\r", "•") + "====\n" + req + "====\n");
                    if (!dry) {
                        Files.write(file, lines);
                    }
                } else {
                    LOGGER.info("+ {} untouched   : {}", String.format("%-8s", name), file);
                }
            }
        } catch (IOException e) {
            throw new Error("could not overwrite file for " + name + " : " + file, e);
        }
    }

    static Optional<String> getExtension(Path p) {
        return getExtension(p.getFileName().toString());
    }

    static Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".") + 1));
    }
}
