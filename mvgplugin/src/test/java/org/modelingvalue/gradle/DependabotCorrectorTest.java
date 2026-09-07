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

package org.modelingvalue.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modelingvalue.gradle.mvgplugin.DependabotCorrector;

public class DependabotCorrectorTest {
    private static final List<String> TEMPLATE = List.of(
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

    private static Path dependabotFile(Path root) throws IOException {
        Files.createDirectories(root.resolve(".github"));
        return root.resolve(".github").resolve("dependabot.yml");
    }

    private static List<String> generate(Path root) throws IOException {
        new DependabotCorrector(null).generate(root);
        return Files.readAllLines(dependabotFile(root));
    }

    @Test
    public void withoutPackageJsonTemplateOnly(@TempDir Path root) throws IOException {
        dependabotFile(root);
        assertEquals(TEMPLATE, generate(root));
    }

    @Test
    public void npmEntryPerPackageJsonDir(@TempDir Path root) throws IOException {
        dependabotFile(root);
        Files.createDirectories(root.resolve("website/src/main/frontend"));
        Files.createDirectories(root.resolve("lsp/plugins/vscode"));
        Files.writeString(root.resolve("website/src/main/frontend/package.json"), "{}");
        Files.writeString(root.resolve("lsp/plugins/vscode/package.json"), "{}");

        List<String> expected = new java.util.ArrayList<>(TEMPLATE);
        expected.addAll(List.of(
                "  - package-ecosystem: \"npm\"",
                "    directory: \"/lsp/plugins/vscode\"",
                "    target-branch: \"develop\"",
                "    schedule:",
                "      interval: \"daily\"",
                "  - package-ecosystem: \"npm\"",
                "    directory: \"/website/src/main/frontend\"",
                "    target-branch: \"develop\"",
                "    schedule:",
                "      interval: \"daily\""
        ));
        assertEquals(expected, generate(root));
    }

    @Test
    public void excludedDirsAreIgnored(@TempDir Path root) throws IOException {
        dependabotFile(root);
        Files.createDirectories(root.resolve("frontend/node_modules/somelib"));
        Files.createDirectories(root.resolve("build/generated"));
        Files.createDirectories(root.resolve(".hidden"));
        Files.writeString(root.resolve("frontend/node_modules/somelib/package.json"), "{}");
        Files.writeString(root.resolve("build/generated/package.json"), "{}");
        Files.writeString(root.resolve(".hidden/package.json"), "{}");
        assertEquals(TEMPLATE, generate(root));
    }

    @Test
    public void rootPackageJsonYieldsRootNpmEntry(@TempDir Path root) throws IOException {
        dependabotFile(root);
        Files.writeString(root.resolve("package.json"), "{}");

        List<String> expected = new java.util.ArrayList<>(TEMPLATE);
        expected.addAll(List.of(
                "  - package-ecosystem: \"npm\"",
                "    directory: \"/\"",
                "    target-branch: \"develop\"",
                "    schedule:",
                "      interval: \"daily\""
        ));
        assertEquals(expected, generate(root));
    }

    @Test
    public void generateIsIdempotent(@TempDir Path root) throws IOException {
        dependabotFile(root);
        Files.createDirectories(root.resolve("frontend"));
        Files.writeString(root.resolve("frontend/package.json"), "{}");

        DependabotCorrector first = new DependabotCorrector(null).generate(root);
        assertEquals(1, first.getChangedFiles(root).size());

        DependabotCorrector second = new DependabotCorrector(null).generate(root);
        assertTrue(second.getChangedFiles(root).isEmpty());
    }

    @Test
    public void notouchLeavesFileAlone(@TempDir Path root) throws IOException {
        Path file = dependabotFile(root);
        Files.write(file, List.of("#notouch", "version: 2"));
        DependabotCorrector corrector = new DependabotCorrector(null).generate(root);
        assertTrue(corrector.getChangedFiles(root).isEmpty());
        assertEquals(List.of("#notouch", "version: 2"), Files.readAllLines(file));
    }
}
