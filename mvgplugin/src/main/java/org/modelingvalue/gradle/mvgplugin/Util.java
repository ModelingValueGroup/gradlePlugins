//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.gradle.api.GradleException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Util {
    public static final int    NUM_CHARS_TO_SHOW_OF_SECRETS = 6;
    public static final String TEST_MARKER_REPLACE_DONE     = getTestMarker("r+");
    public static final String TEST_MARKER_REPLACE_NOT_DONE = getTestMarker("r-");
    public static final String TEST_MARKER_TRIGGER          = getTestMarker("!");
    public static final String TEST_MARKER_TESTING          = getTestMarker("TESTING");
    public static final String TEST_MARKER_TRIGGERING       = getTestMarker("triggering");

    private static String getTestMarker(String m) {
        return "•" + m + "•";
    }

    public static URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new GradleException("not a valid url in header: " + url, e);
        }
    }

    public static List<String> readAllLines(Path f) throws IOException {
        return Files.readAllLines(f);
    }

    public static long getFileSize(Path f) throws IOException {
        return Files.size(f);
    }

    public static List<String> download(URL url) {
        try (InputStream in = url.openStream()) {
            return Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            LOGGER.info("+ mvg: failure getting file from: {} ({})", url, e.getMessage());
            return null;
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

    public static boolean envOrPropBoolean(String name) {
        return Boolean.parseBoolean(envOrProp(name, "false"));
    }

    public static String envOrProp(String name, String def) {
        String value =
                elvis(getSystemProperty(name),
                        () -> elvis(getSystemEnv(name),
                                () -> elvis(InfoGradle.getGradleDotProperties().getProp(name),
                                        () -> def)));
        LOGGER.info("+ mvg: envOrProp        : {} => {}", name, Util.hide(value));
        return value;
    }

    public static String getSystemProperty(String name) {
        String value = System.getProperty(name);
        LOGGER.info("+ mvg: getSystemProperty: {} => {}", name, Util.hide(value));
        return value;
    }

    public static String getSystemEnv(String name) {
        String value = System.getenv(name);
        LOGGER.info("+ mvg: getSystemEnv     : {} => {}", name, Util.hide(value));
        return value;
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

    static Map<?, ?> readYaml(Path path) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.findAndRegisterModules();
        return objectMapper.readValue(path.toFile(), Map.class);
    }

    static Version getMyPluginVersion() {
        URL location = Util.class.getProtectionDomain().getCodeSource().getLocation();
        if (location.getFile().isEmpty()) {
            return null;
        }
        String  versionString = location.getFile().replaceAll(".*/", "").replaceAll("[.]jar$", "").replaceAll("^.*-", "");
        Version version       = new Version(versionString);
        return version.valid() ? version : null;
    }

    public static long toBytes(String in) {
        in = in.trim();
        if (in.matches("[0-9]+")) {
            return Long.parseLong(in);
        } else if (in.matches("[0-9]+[kKmMgGtT]?")) {
            long l = Long.parseLong(in, 0, in.length() - 1, 10);
            switch (Character.toLowerCase(in.charAt(in.length() - 1))) {
            case 'k':
                return l * 1024;
            case 'm':
                return l * 1024 * 1024;
            case 'g':
                return l * 1024 * 1024 * 1024;
            case 't':
                return l * 1024 * 1024 * 1024 * 1024;
            }
            return -1; // never reached
        } else {
            throw new GradleException("human readable form of memory size '" + in + "' is not valid");
        }
    }

    public static Optional<String> getExtension(Path p) {
        return getExtension(p.getFileName().toString());
    }

    public static Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".") + 1));
    }

    public static Properties loadProperties(Path file) {
        try (InputStream s = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(s);
            return props;
        } catch (IOException e) {
            throw new GradleException("can not read properties file at " + file.toAbsolutePath(), e);
        }
    }

    public static String hide(String secret) {
        if (secret == null || secret.length() < 8) { // real secrets are always of length 8+ so we can show if below
            return secret;
        } else {
            return secret.substring(0, NUM_CHARS_TO_SHOW_OF_SECRETS) + "*".repeat(secret.length() - NUM_CHARS_TO_SHOW_OF_SECRETS);
        }
    }

    public static boolean isWindows() {
        String prop = System.getProperty("os.name");
        return prop == null || prop.toLowerCase().startsWith("windows");
    }

    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "UNKNOWN_HOSTNAME";
        }
    }

    public static Stream<Node> childStream(Node node) {
        return asStream(node.getChildNodes());
    }

    public static List<Node> asList(NodeList nodeList) {
        return asStream(nodeList).collect(Collectors.toList());
    }

    public static Stream<Node> asStream(NodeList nodeList) {
        return StreamSupport.stream(asIterable(nodeList).spliterator(), false);
    }

    public static Iterable<Node> asIterable(NodeList nodeList) {
        return () -> new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < nodeList.getLength();
            }

            @Override
            public Node next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return nodeList.item(index++);
            }
        };
    }

    public static String getAttributeText(Node node, String name) {
        Node namedItem = node.getAttributes().getNamedItem(name);
        return namedItem == null ? null : namedItem.getNodeValue();
    }

    public static List<String> getMultiChildText(Node node, String name) {
        return childStream(node)
                .filter(c -> c.getNodeName().equals(name))
                .map(n -> n.getChildNodes().item(0).getTextContent())
                .collect(Collectors.toList());
    }

    public static String getSingleChildText(Node node, String name) {
        //        long count = childStream(node).filter(c -> c.getNodeName().equals(name)).count();
        //        if (1 < count) {
        //            System.err.println("WARNING: there are " + count + " children named " + name);
        //        }
        return childStream(node)
                .filter(c -> c.getNodeName().equals(name))
                .filter(n -> n.getChildNodes().getLength() == 1)
                .map(n -> n.getChildNodes().item(0).getTextContent())
                .findFirst()
                .orElse(null);
    }

    public static Node getSingleChildNode(Node node, String name) {
        //        long count = childStream(node).filter(c -> c.getNodeName().equals(name)).count();
        //        if (1 < count) {
        //            System.err.println("WARNING: there are " + count + " children named " + name);
        //        }
        return childStream(node)
                .filter(c -> c.getNodeName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static String makeSplitterPattern(String sep) {
        return "(" + Pattern.quote(sep) + ")+";
    }

    public static void removeTmpDir(Path d) throws IOException {
        if (!d.toAbsolutePath().toString().contains("/tmp/")) {
            throw new Error("not allowed to completely remove tmp dir (no /tmp/ in the name: " + d.toAbsolutePath());
        }
        for (int i = 0; Files.isDirectory(d) && i < 10; i++) {
            Files.find(d, 3, (p, a) -> true)
                    //.peek(p -> System.out.println("DEL " + p.toAbsolutePath()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (DirectoryNotEmptyException e) {
                            // ignore
                        } catch (IOException e) {
                            throw new Error(e);
                        }
                    });
        }
    }
}
