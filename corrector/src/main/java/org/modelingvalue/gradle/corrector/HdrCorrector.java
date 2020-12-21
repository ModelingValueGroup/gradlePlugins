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
import static org.modelingvalue.gradle.corrector.Info.LOGGER;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class HdrCorrector extends CorrectorBase {
    private final Map<String, String>       extensions;
    private final List<String>              headerLines;
    private final Map<String, List<String>> ext2header = new HashMap<>();

    public HdrCorrector(CorrectorExtension ext) {
        super("header", ext.getRoot(), ext.getHeaderFileExcludes());
        extensions = ext.getHeaderFileExtensions();
        URL headerUrl = ext.getHeaderUrl();
        headerLines = Util.downloadAndSubstitute(getVarMapping(), headerUrl);
        if (LOGGER.isTraceEnabled()) {
            extensions.forEach((e, p) -> LOGGER.trace("# header extensions      : " + e + " (" + p + ")"));
            LOGGER.trace("# header                 : {}", headerUrl);
        }
    }

    private static Map<String, String> getVarMapping() {
        return Map.of("yyyy", "" + LocalDateTime.now().getYear());
    }

    public HdrCorrector generate() throws IOException {
        allFiles().forEach(this::replaceHeader);
        return this;
    }

    private void replaceHeader(Path f) {
        if (needsHeader(f)) {
            String       ext        = getExtension(f).orElseThrow();
            List<String> header     = ext2header.computeIfAbsent(ext, e -> border(extensions.get(e)));
            List<String> lines      = Util.readAllLines(f);
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
        return ext.isPresent() && Util.getFileSize(f) != 0 && extensions.containsKey(ext.get());
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
}
