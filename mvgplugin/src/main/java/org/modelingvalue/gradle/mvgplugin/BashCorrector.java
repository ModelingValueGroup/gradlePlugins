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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class BashCorrector extends TreeCorrector {
    public static final String CORRECTOR_EXT = ".corrector.sh";

    public BashCorrector(MvgCorrectorExtension ext) {
        super("bash", InfoGradle.getAbsProjectDir(), ext.getBashFileExcludes());
    }

    public Set<Path> getChangedFiles() {
        return super.getChangedFiles(InfoGradle.getAbsProjectDir());
    }

    public BashCorrector generate() throws IOException {
        allFiles()
                .filter(p -> p.getFileName().toString().matches(".*" + Pattern.quote(CORRECTOR_EXT)))
                .forEach(script -> {
                    String simpleScriptName = script.getFileName().toString();
                    if (Util.isWindows()) {
                        LOGGER.info("+ mvg: NOT running bash corrector {} on windows for obvious reasons", simpleScriptName);
                    } else {
                        try {
                            LOGGER.info("+ mvg: running {}", script);
                            BashRunner bashRunner = new BashRunner(script).waitForExit();
                            if (bashRunner.exitValue() != 0) {
                                LOGGER.error("run of script {} resulted in an error ({})", script, bashRunner.exitValue());
                            } else {
                                List<String> stderr = bashRunner.getStderr();
                                if (!stderr.isEmpty()) {
                                    LOGGER.info("+ mvg: running {} produced messages on stderr:", simpleScriptName);
                                    stderr.forEach(line -> LOGGER.info("+ mvg:     {}", line));
                                }
                                Path outFile = script.getParent().resolve(simpleScriptName.replaceFirst(Pattern.quote(CORRECTOR_EXT) + "$", ""));
                                overwrite(outFile, bashRunner.getStdout());
                            }
                        } catch (IOException e) {
                            LOGGER.error("could not run {}: {} (ignored for now)", simpleScriptName, e.getMessage());
                        }
                    }
                });
        return this;
    }
}
