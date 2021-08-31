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
import java.util.Optional;
import java.util.Set;

public class EolCorrector extends TreeCorrector {
    private final Set<String> textExtensions;
    private final Set<String> noTextExtensions;
    private final Set<String> textFiles;
    private final Set<String> noTextFiles;

    public EolCorrector(MvgCorrectorExtension ext) {
        super("eols  ", ext.getRoot(), ext.getEolFileExcludes());
        textExtensions = ext.getTextFileExtensions();
        noTextExtensions = ext.getNoTextFileExtensions();
        textFiles = ext.getTextFiles();
        noTextFiles = ext.getNoTextFiles();
        if (LOGGER.isDebugEnabled()) {
            textExtensions/*  */.forEach(x -> LOGGER.debug("++ # eols   textExtensions  : " + x));
            noTextExtensions/**/.forEach(x -> LOGGER.debug("++ # eols   noTextExtensions: " + x));
            textFiles/*       */.forEach(x -> LOGGER.debug("++ # eols   textFiles       : " + x));
            noTextFiles/*     */.forEach(x -> LOGGER.debug("++ # eols   noTextFiles     : " + x));
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
            LOGGER.info("IOException '{}' detected (and ignored) on file {}", e.getMessage(), f.toAbsolutePath());
        }
    }

    private boolean isTextType(Path f) {
        String           filename = f.getFileName().toString();
        Optional<String> ext      = Util.getExtension(filename);
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
