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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

public class Util {
    public static URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new Error("not a valid url in header: " + url, e);
        }
    }

    public static List<String> readAllLines(Path f) {
        try {
            return Files.readAllLines(f);
        } catch (IOException e) {
            throw new Error("could not read lines: " + f, e);
        }
    }

    public static long getFileSize(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            throw new Error("file size failed", e);
        }
    }

    public static List<String> downloadAndSubstitute(Map<String, String> vars, URL url) {
        try (InputStream in = url.openStream()) {
            List<String> lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
            return replaceVars(vars, lines);
        } catch (IOException e) {
            throw new Error("can not get lines from " + url, e);
        }
    }

    public static List<String> replaceVars(Map<String, String> vars, List<String> lines) {
        return lines.stream().map(line -> replaceVars(vars, line)).collect(Collectors.toList());
    }

    public static String replaceVars(Map<String, String> vars, String line) {
        for (Entry<String, String> entry : vars.entrySet()) {
            line = line.replaceAll(entry.getKey(), entry.getValue());
        }
        return line;
    }

    public static String envOrProp(String name, String def) {
        return elvis(System.getProperty(name), () -> elvis(System.getenv(name), () -> def));
    }

    public static <T> T elvis(T o, Supplier<T> f) {
        return o != null ? o : f == null ? null : f.get();
    }

    public static int numOccurences(String find, String all) {
        int count     = 0;
        int fromIndex = 0;
        while ((fromIndex = all.indexOf(find, fromIndex)) != -1) {
            count++;
            fromIndex++;
        }
        return count;
    }

    public static URI makeURL(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new GradleException("unexpected exception", e);
        }
    }
}
