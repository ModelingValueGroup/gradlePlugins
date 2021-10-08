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
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

@SuppressWarnings({"WeakerAccess"})
public abstract class TreeCorrector extends Corrector {
    private final Path        root;
    private final Set<String> excludes;

    public TreeCorrector(String name, Path root, Set<String> excludes) {
        super(name);
        this.root = root;
        this.excludes = excludes;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("++ mvg: ========================================");
            excludes.forEach(x -> LOGGER.debug("++ mvg: # " + name + " excludes        : " + x));
        }
    }

    protected Stream<Path> allFiles() throws IOException {
        return Files.walk(root).filter(this::filter);
    }

    public Path getRoot() {
        return root;
    }

    public Set<Path> getChangedFiles() {
        return getChangedFiles(this.root);
    }

    protected boolean filter(Path p) {
        return Files.isRegularFile(p) && excludes.stream().noneMatch(pattern -> {
            String p1 = root.relativize(p).toString();
            String p2 = Paths.get(".").resolve(p1).toString();
            return p1.matches(pattern) || p2.matches(pattern);
        });
    }
}
