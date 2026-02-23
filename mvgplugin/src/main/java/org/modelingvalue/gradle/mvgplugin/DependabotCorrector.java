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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class DependabotCorrector extends Corrector {
    private final MvgCorrectorExtension ext;

    public DependabotCorrector(MvgCorrectorExtension ext) {
        super("dependabot");
        this.ext = ext;
    }

    public DependabotCorrector generate() throws IOException {
        Path dependabotFile = InfoGradle.getAbsProjectDir().resolve(".github").resolve("dependabot.yml");
        if (!Files.isRegularFile(dependabotFile) || Files.readAllLines(dependabotFile).stream().noneMatch(l -> l.contains("#notouch"))) {
            overwrite(dependabotFile, getFileContents());
        }
        return this;
    }

    public Set<Path> getChangedFiles() {
        return super.getChangedFiles(InfoGradle.getAbsProjectDir());
    }

    private List<String> getFileContents() {
        return List.of(
                "version: 2",
                "updates:",
                "  - package-ecosystem: \"gradle\"",
                "    directory: \"/\"",
                "    target-branch: \"develop\"",
                "    schedule:",
                "      interval: \"daily\"",
                "  - package-ecosystem: \"github-actions\"",
                "    directory: \"/\"",
                "    target-branch: \"develop\"",
                "    schedule:",
                "      interval: \"daily\""
        );
    }
}
