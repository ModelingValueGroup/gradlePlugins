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

import static java.lang.Integer.min;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class HeaderCorrector extends CorrectorBase {
    private static final Logger                    LOGGER     = Logging.getLogger(MvgCorrectorPluginExtension.NAME);
    //
    private final        Map<String, String>       extensions;
    private final        List<String>              headerLines;
    private final        Map<String, List<String>> ext2header = new HashMap<>();

    public HeaderCorrector(MvgCorrectorPluginExtension ext) {
        super("header", ext.getRoot(), ext.getHeaderFileExcludes(), ext.getDry());
        extensions = ext.getHeaderFileExtensions();
        URL headerUrl = ext.getHeaderUrl();
        headerLines = downloadHeaderLines(headerUrl);
        if (LOGGER.isTraceEnabled()) {
            extensions.forEach((e, p) -> LOGGER.trace("# header extensions      : " + e + " (" + p + ")"));
            LOGGER.trace("# header                 : {}", headerUrl);
        }
    }

    public HeaderCorrector generate() throws IOException {
        allFiles().forEach(this::replaceHeader);
        return this;
    }

    private static List<String> downloadHeaderLines(URL headerUrl) {
        try (InputStream in = headerUrl.openStream()) {
            return replaceVars(Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")));
        } catch (IOException e) {
            throw new Error("can not get header from " + headerUrl, e);
        }
    }

    private static List<String> replaceVars(List<String> lines) {
        return lines.stream().map(HeaderCorrector::replaceVars).collect(Collectors.toList());
    }

    private static String replaceVars(String line) {
        return line.replaceAll("yyyy", "" + LocalDateTime.now().getYear());
    }

    private void replaceHeader(Path f) {
        if (needsHeader(f)) {
            String       ext        = getExtension(f).orElseThrow();
            List<String> header     = ext2header.computeIfAbsent(ext, e -> border(extensions.get(e)));
            List<String> lines      = readAllLines(f);
            boolean      isHashBang = !lines.isEmpty() && lines.get(0).startsWith("#!");
            int          baseIndex  = isHashBang ? 1 : 0;
            while (baseIndex < lines.size() && isHeaderLine(lines.get(baseIndex), ext)) {
                lines.remove(baseIndex);
            }
            isHashBang = !lines.isEmpty() && lines.get(0).startsWith("#!");
            baseIndex = isHashBang ? 1 : 0;
            lines.addAll(baseIndex, header);
            overwrite(f, lines);
        }
    }

    private boolean needsHeader(Path f) {
        Optional<String> ext = getExtension(f);
        return ext.isPresent() && getFileSize(f) != 0 && extensions.containsKey(ext.get());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private List<String> border(String pre) {
        List<String> cleaned  = cleanup(pre, headerLines);
        int          len      = cleaned.stream().mapToInt(String::length).max().getAsInt();
        String       border   = pre + "~" + String.format("%" + len + "s", "").replace(' ', '~') + "~~";
        List<String> bordered = cleaned.stream().map(l -> String.format(pre + " %-" + len + "s ~", l)).collect(Collectors.toList());
        bordered.add(0, border);
        bordered.add(border);
        bordered.add("");
        return bordered;
    }

    private List<String> cleanup(String pre, List<String> inFile) {
        List<String> h = inFile
                .stream()
                .map(String::stripTrailing)
                .filter(l -> !l.matches("^" + pre + "~~*$") && !l.matches("^//" + "~~*$"))
                .map(l -> l.replaceAll("^" + pre, ""))
                .map(l -> l.replaceAll("^//", ""))
                .map(l -> l.replaceAll("~$", ""))
                .map(String::stripTrailing)
                .collect(Collectors.toList());
        int indent = calcIndent(h);
        if (0 < indent) {
            h = h.stream().map(l -> l.substring(min(l.length(), indent))).collect(Collectors.toList());
        }
        while (!h.isEmpty() && h.get(0).trim().isEmpty()) {
            h.remove(0);
        }
        while (!h.isEmpty() && h.get(h.size() - 1).trim().isEmpty()) {
            h.remove(h.size() - 1);
        }
        if (h.isEmpty()) {
            h.add("no header available");
        }
        return h;
    }

    private boolean isHeaderLine(String line, String ext) {
        return (line.startsWith(extensions.get(ext)) && line.endsWith("~")) || line.trim().isEmpty();
    }

    private int calcIndent(List<String> h) {
        int indent = Integer.MAX_VALUE;
        for (String l : h) {
            if (l.trim().length() != 0) {
                indent = min(indent, l.replaceAll("[^ ].*", "").length());
            }
        }
        return indent;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static List<String> readAllLines(Path f) {
        try {
            return Files.readAllLines(f);
        } catch (IOException e) {
            throw new Error("could not read lines: " + f, e);
        }
    }

    private static long getFileSize(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            throw new Error("file size failed", e);
        }
    }
}
