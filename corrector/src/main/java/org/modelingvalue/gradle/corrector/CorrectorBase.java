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

import static org.modelingvalue.gradle.corrector.Info.LOGGER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"WeakerAccess"})
public abstract class CorrectorBase {
    private final String      name;
    private final Path        root;
    private final Set<String> excludes;
    private final Set<Path>   changedFiles = new HashSet<>();

    public CorrectorBase(String name, Path root, Set<String> excludes) {
        this.name = name;
        this.root = root;
        this.excludes = excludes;
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("++ ========================================");
            excludes.forEach(x -> LOGGER.trace("++ # " + name + " excludes        : " + x));
        }
    }

    protected Stream<Path> allFiles() throws IOException {
        return Files.walk(root).filter(this::filter);
    }

    public Path getRoot() {
        return root;
    }

    public Set<Path> getChangedFiles() {
        return changedFiles.stream().map(root::relativize).collect(Collectors.toSet());
    }

    protected boolean filter(Path p) {
        return Files.isRegularFile(p) && excludes.stream().noneMatch(pattern -> {
            String p1 = root.relativize(p).toString();
            String p2 = Paths.get(".").resolve(p1).toString();
            return p1.matches(pattern) || p2.matches(pattern);
        });
    }

    protected void overwrite(Path file, List<String> lines) {
        try {
            if (!Files.isRegularFile(file)) {
                LOGGER.info("+ {} generated   : {}", name, file);
                Files.write(file, lines);
                changedFiles.add(file);
            } else {
                String was = Files.readString(file);
                String req = String.join("\n", lines);
                if (0 < was.length() && was.endsWith("\n")) {
                    req += "\n";
                }
                if (!req.equals(was)) {
                    LOGGER.info("+ {} regenerated : {}", name, file);
                    LOGGER.trace("++ ====\n" + was.replaceAll("\r", "•") + "====\n" + req + "====\n");
                    Files.write(file, lines);
                    changedFiles.add(file);
                } else {
                    LOGGER.info("+ {} untouched   : {}", name, file);
                }
            }
        } catch (IOException e) {
            throw new Error("could not overwrite file for " + name + " : " + file, e);
        }
    }

    protected static Optional<String> getExtension(Path p) {
        return getExtension(p.getFileName().toString());
    }

    protected static Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".") + 1));
    }
}
