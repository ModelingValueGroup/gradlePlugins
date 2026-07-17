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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.mvgplugin.MvgCentralPublisher;

public class CentralBundleTest {
    @Test
    public void bundle(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path staging = tmp.resolve("staging");
        Path artDir  = staging.resolve("org/modelingvalue/dummy/1.0.0");
        Files.createDirectories(artDir);
        Files.writeString(artDir.resolve("dummy-1.0.0.jar"), "jarbytes");
        Files.writeString(artDir.resolve("dummy-1.0.0.pom"), "pombytes");
        Files.writeString(artDir.resolve("dummy-1.0.0.pom.sha1"), "already-there");
        Files.writeString(artDir.resolve("dummy-1.0.0.jar.asc"), "signature");
        Files.writeString(staging.resolve("org/modelingvalue/dummy").resolve("maven-metadata.xml"), "meta");

        Path bundle = tmp.resolve("bundle.zip");
        MvgCentralPublisher.createBundle(staging, bundle);

        Set<String> entries = new TreeSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                entries.add(e.getName());
            }
        }
        String prefix = "org/modelingvalue/dummy/1.0.0/dummy-1.0.0";
        assertTrue(entries.contains(prefix + ".jar"));
        assertTrue(entries.contains(prefix + ".jar.asc"));
        assertTrue(entries.contains(prefix + ".jar.md5"), "missing checksum should have been generated");
        assertTrue(entries.contains(prefix + ".jar.sha1"), "missing checksum should have been generated");
        assertTrue(entries.contains(prefix + ".pom.sha1"), "existing checksum should have been kept");
        assertFalse(entries.stream().anyMatch(n -> n.contains("maven-metadata")), "maven-metadata must not be bundled");
        assertFalse(entries.contains(prefix + ".jar.asc.md5"), "signatures must not get checksums");

        assertEquals("already-there", Files.readString(artDir.resolve("dummy-1.0.0.pom.sha1")), "existing checksum must not be overwritten");
        assertEquals(MvgCentralPublisher.checksum(artDir.resolve("dummy-1.0.0.jar"), "MD5"), Files.readString(artDir.resolve("dummy-1.0.0.jar.md5")));
    }

    @Test
    public void checksums() throws IOException {
        Path f = Files.createTempFile("checksum", ".txt");
        Files.writeString(f, "hello");
        // well-known digests of the string "hello"
        assertEquals("5d41402abc4b2a76b9719d911017c592", MvgCentralPublisher.checksum(f, "MD5"));
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", MvgCentralPublisher.checksum(f, "SHA-1"));
        Files.delete(f);
    }
}
