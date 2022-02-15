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

import static java.lang.String.join;
import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;
import static org.gradle.internal.impldep.org.junit.Assert.assertTrue;
import static org.modelingvalue.gradle.mvgplugin.MpsPluginDownloader.PLUGIN_MAIN_SEP;
import static org.modelingvalue.gradle.mvgplugin.MpsPluginDownloader.PLUGIN_SUB_SEP;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MpsPluginDownloaderTest {
    static {
        System.setProperty("ALL_TO_STDERR", "true"); // so we get logging from Info.LOGGER in stderr
    }

    private static final String PLUGIN_MPS = join(PLUGIN_MAIN_SEP, List.of(
            join(PLUGIN_SUB_SEP, List.of("jetbrains.mps.baseLanguage.extensions")),   // simple id      => latest stable release for the used MPS version                        => error if not found
            join(PLUGIN_SUB_SEP, List.of("org.jetbrains.IdeaVim-EasyMotion", "1.3")), // id and version => just the exact version                                                => error if not found
            join(PLUGIN_SUB_SEP, List.of("aws.toolkit", "eap")),                      // id and channel => latest channel release for the used MPS version                       => try latest release version if not found
            join(PLUGIN_SUB_SEP, List.of("DclareForMPS", "BRANCHED"))                 // id BRANCHED    => use the branch name as the channel name and find the latest version   => try eap and then stable release
    ));

    @Test
    void listPluginIds() {
        List<String> allPluginsIds = new JetBrainsPluginRepoRestApi().getPluginsXmlIds();

        // just check a few names:
        assertTrue(allPluginsIds.contains("jetbrains.mps.baseLanguage.extensions"));
        assertTrue(allPluginsIds.contains("org.jetbrains.IdeaVim-EasyMotion"));
        assertTrue(allPluginsIds.contains("aws.toolkit"));
        assertTrue(allPluginsIds.contains("DclareForMPS"));
        assertTrue(allPluginsIds.contains("com.jetbrains.CyanTheme"));
        assertTrue(allPluginsIds.contains("ColourChooser"));
        assertTrue(allPluginsIds.contains("DclareForMPS"));
        assertTrue(allPluginsIds.contains("cn.hexinfo.devops.jetbrains-rdm"));
        assertTrue(allPluginsIds.contains("com.ecarx.t2bg.commit"));

        // check that all names only contain a certain subset of charecters:
        String charSet = "->/:'. _(),+@a-zA-Z0-9";
        assertEquals(List.of(), allPluginsIds.stream()
                .flatMapToInt(String::codePoints)
                .sorted()
                .distinct()
                .mapToObj(Character::toString)
                .filter(s -> !s.matches("[" + charSet + "]"))
                .collect(Collectors.toList()));
    }

    @Test
    void downloadPlugins() throws IOException {
        Path TMP_DIR = Path.of("build", "tmp", "downloadedPlugins");
        Util.removeTmpDir(TMP_DIR);

        MpsPluginDownloader mpsPluginDownloader = new MpsPluginDownloader();
        for (String mpsBuildNumber : List.of("MPS-212.5284.1281", "MPS-193.1223")) {
            for (String channel : List.of("", "eap", "xyzzy")) {
                Path tmp = TMP_DIR.resolve(mpsBuildNumber).resolve(channel);
                //noinspection ResultOfMethodCallIgnored
                mpsPluginDownloader.downloadAllPlugins(tmp, PLUGIN_MPS, mpsBuildNumber, channel).collect(Collectors.toList());
            }
        }

        List<Path> zips = Files.walk(TMP_DIR)
                .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".zip"))
                .collect(Collectors.toList());
        long distictNames = zips.stream().map(f -> f.getFileName().toString()).distinct().count();
        long distictSizes = zips.stream().mapToLong(this::filesSize).distinct().count();

        // actual downloads is moving target, so only check some numbers:
        assertEquals(22, zips.size());
        assertEquals(4, distictNames);
        assertEquals(7, distictSizes);
    }

    private long filesSize(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}