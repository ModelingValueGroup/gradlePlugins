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
import java.util.Optional;
import java.util.Set;

public class EolCorrector extends CorrectorBase {
    private final        Set<String> textExtensions;
    private final        Set<String> noTextExtensions;
    private final        Set<String> textFiles;
    private final        Set<String> noTextFiles;

    public EolCorrector(CorrectorExtension ext) {
        super("eols  ", ext.getRoot(), ext.getEolFileExcludes());
        textExtensions = ext.getTextFileExtensions();
        noTextExtensions = ext.getNoTextFileExtensions();
        textFiles = ext.getTextFiles();
        noTextFiles = ext.getNoTextFiles();
        if (LOGGER.isTraceEnabled()) {
            textExtensions/*  */.forEach(x -> LOGGER.trace("++ # eols   textExtensions  : " + x));
            noTextExtensions/**/.forEach(x -> LOGGER.trace("++ # eols   noTextExtensions: " + x));
            textFiles/*       */.forEach(x -> LOGGER.trace("++ # eols   textFiles       : " + x));
            noTextFiles/*     */.forEach(x -> LOGGER.trace("++ # eols   noTextFiles     : " + x));
        }
    }

    public EolCorrector generate() throws IOException {
        allFiles()
                .filter(this::isTextType)
                .forEach(this::correctCRLF);
        return this;
    }

    private void correctCRLF(Path f) {
        try {
            overwrite(f, Files.readAllLines(f));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isTextType(Path f) {
        String           filename = f.getFileName().toString();
        Optional<String> ext      = getExtension(filename);
        if (size(f) == 0L) {
            return false;
        }
        if (textFiles.contains(filename)) {
            return true;
        }
        if (noTextFiles.contains(filename)) {
            return false;
        }
        if (ext.isEmpty()) {
            return false;
        }
        if (textExtensions.contains(ext.get())) {
            return true;
        }
        if (noTextExtensions.contains(ext.get())) {
            return false;
        }
        LOGGER.info("+ unknown file type (not correcting EOLs): {}", f);
        return false;
    }

    private long size(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            throw new Error("file size failed", e);
        }
    }
}
