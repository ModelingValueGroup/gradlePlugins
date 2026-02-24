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

package org.modelingvalue.gradle.mvgplugin;

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

public class MvgMps {
    private final static int    MAX_REDIRECTS = 10;
    private final static Object LOAD_LOCK     = new Object();

    private final Gradle                  gradle;
    private final MvgMpsExtension         ext;
    private       boolean                 mpsHasBeenLoaded;
    private       Path                    rootPath;
    private final Map<String, Path>       jarIndex       = new HashMap<>();
    private final Map<String, List<Path>> ambiguousIndex = new HashMap<>();
    private       Properties              mpsBuildProps;
    private       Version                 mpsBuildNumber;

    public MvgMps(Gradle gradle) {
        this.gradle = gradle;
        ext = MvgMpsExtension.make(gradle);
    }

    public Object resolveMpsDependency(String dep) {
        Path jar = getJarIndex().get(dep);
        if (jar == null) {
            List<Path> candidates = ambiguousIndex.get(dep);
            if (candidates != null) {
                throw new GradleException("no jar found for '" + dep + "' in " + ext.getMpsInstallDir() + " (do you mean one of these: " + candidates + "?)");
            } else {
                throw new GradleException("no jar found for '" + dep + "' in " + ext.getMpsInstallDir());
            }
        }
        //TODO:  build/test-workspace/gradlePlugins/build/MPS-2020.3/MPS 2020.3/lib/MPS-src.zip
        LOGGER.info("+ mvg-mps: dependency replaced: {} => {}", dep, jar);
        return gradle.getRootProject().files(jar.toFile());
    }

    private Map<String, Path> getJarIndex() {
        loadMps();
        return jarIndex;
    }

    private void loadMps() {
        synchronized (LOAD_LOCK) { // we will be careful, you never know if gradle will call us at the same time in multiple Threads
            if (!mpsHasBeenLoaded) {
                assert jarIndex.isEmpty();
                assert ambiguousIndex.isEmpty();
                assert mpsBuildProps == null;
                assert mpsBuildNumber == null;

                downloadAndUnzip(ext);

                rootPath = ext.getMpsInstallDir().toPath();
                makeJarIndex();
                mpsBuildProps = readMpsBuildProps();
                mpsBuildNumber = getBuildNumber();
                checkAntFilesAgainstMpsBuildNumber();

                LOGGER.info("+ mvg-mps: indexed {} jars in MPS dir {}", jarIndex.size(), ext.getMpsInstallDir());
                LOGGER.info("+ mvg-mps: loaded MPS properties, mps.build.number={}", mpsBuildNumber);

                mpsHasBeenLoaded = true;
                assert !jarIndex.isEmpty();
                assert mpsBuildProps != null;
            }
        }
    }

    private void checkAntFilesAgainstMpsBuildNumber() {
        try (Stream<Path> pathStream = Files.walk(InfoGradle.getAbsProjectDir())) {
            pathStream
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .map(AntFileMpsVersionsExtractor::new)
                    .filter(AntFileMpsVersionsExtractor::isAntFile)
                    .filter(AntFileMpsVersionsExtractor::hasRange)
                    .forEach(a -> {
                        if (a.getSince() != null && mpsBuildNumber.compareTo(a.getSince()) < 0) {
                            LOGGER.warn("+ mvg-mps: the MPS build number {} of MPS {} is below the range [{}...{}] mentioned in ant file: {}", mpsBuildNumber, ext.getVersion(), a.getSince(), a.getUntil(), a.getFile());
                        } else if (a.getUntil() != null && 0 < mpsBuildNumber.compareTo(a.getUntil())) {
                            LOGGER.warn("+ mvg-mps: the MPS build number {} of MPS {} is above the range [{}...{}] mentioned in ant file: {}", mpsBuildNumber, ext.getVersion(), a.getSince(), a.getUntil(), a.getFile());
                        } else {
                            LOGGER.info("+ mvg-mps: the MPS build number {} of MPS {} is in range [{}...{}] of the requested in ant file: {}", mpsBuildNumber, ext.getVersion(), a.getSince(), a.getUntil(), a.getFile());
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Properties readMpsBuildProps() {
        Path propFile = ext.getMpsInstallDir().toPath().resolve("build.properties");
        if (!Files.isRegularFile(propFile)) {
            LOGGER.warn("+ mvg-mps: could not read the MPS properties file at {}", propFile.toAbsolutePath());
            return new Properties();
        } else {
            return Util.loadProperties(propFile);
        }
    }

    @NotNull
    private Version getBuildNumber() {
        Object o = mpsBuildProps.get("mps.build.number");
        return new Version(o == null ? "0.0.0" : o.toString().replaceFirst("MPS-", ""));
    }

    private static void downloadAndUnzip(MvgMpsExtension ext) {
        Path mpsCacheFile   = ext.getMpsCacheFile();
        Path mpsDownloadDir = ext.getMpsDownloadDir().toPath();
        Path mpsInstallDir  = ext.getMpsInstallDir().toPath();

        if (!Files.isRegularFile(mpsCacheFile)) {
            LOGGER.info("+ mvg-mps: cached MPS zip is absent, downloading...");
            downloadMps(ext, mpsCacheFile);
        } else if (!isCacheValid(ext, mpsCacheFile)) {
            LOGGER.info("+ mvg-mps: cached MPS zip is stale, re-downloading...");
            downloadMps(ext, mpsCacheFile);
        } else {
            LOGGER.info("+ mvg-mps: cached MPS zip is ok, using cache: {}", mpsCacheFile);
        }
        if (!Files.isDirectory(mpsInstallDir)) {
            unzip(ext, mpsCacheFile, mpsDownloadDir);
        }
    }

    private static boolean isCacheValid(MvgMpsExtension ext, Path mpsCacheFile) {
        try {
            long cachedSize = Files.size(mpsCacheFile);
            long remoteSize = getRemoteFileSize(ext.getMpsDownloadUrl());
            if (remoteSize <= 0) {
                LOGGER.info("+ mvg-mps: could not determine remote file size, trusting cached file");
                return true;
            }
            if (remoteSize == cachedSize) {
                return true;
            }
            LOGGER.info("+ mvg-mps: cache size mismatch: cached={}, remote={}", cachedSize, remoteSize);
            return false;
        } catch (IOException e) {
            LOGGER.info("+ mvg-mps: could not verify cache ({}), trusting cached file", e.getMessage());
            return true;
        }
    }

    private static long getRemoteFileSize(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) Util.getUrl(url).openConnection();
        conn.setRequestMethod("HEAD");
        conn.setInstanceFollowRedirects(true);
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String redirect = conn.getHeaderField("Location");
                if (redirect == null) {
                    break;
                }
                LOGGER.info("+ mvg-mps: HEAD redirect => {}", redirect);
                conn.disconnect();
                conn = (HttpURLConnection) Util.getUrl(redirect).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setInstanceFollowRedirects(true);
            } else {
                break;
            }
        }
        long size = conn.getContentLengthLong();
        conn.disconnect();
        return size;
    }

    private static void downloadMps(MvgMpsExtension ext, Path mpsZip) {
        long   t0  = System.currentTimeMillis();
        String url = ext.getMpsDownloadUrl();
        try {
            Files.createDirectories(mpsZip.getParent());
            URLConnection urlConnection = Util.getUrl(url).openConnection();
            for (int i = 0; i < MAX_REDIRECTS; i++) {
                String redirect = urlConnection.getHeaderField("Location");
                if (redirect == null) {
                    break;
                }
                LOGGER.info("+ mvg-mps: MPS download redirect => {}", redirect);
                urlConnection = Util.getUrl(redirect).openConnection();
            }
            try (InputStream is = urlConnection.getInputStream(); FileOutputStream fos = new FileOutputStream(mpsZip.toFile())) {
                long len = fos.getChannel().transferFrom(Channels.newChannel(is), 0, Long.MAX_VALUE);
                if (len == 0) {
                    Files.deleteIfExists(mpsZip);
                    throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (got empty file)");
                }
                LOGGER.info("+ mvg-mps: read {} bytes of zip file", len);
            }
        } catch (IOException e) {
            throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (" + e.getMessage() + ")", e);
        }
        LOGGER.info("+ mvg-mps: downloading MPS {} took {} ms", ext.getVersion(), System.currentTimeMillis() - t0);
    }

    private static void unzip(MvgMpsExtension ext, Path fileZip, Path destDir) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip.toFile()))) {
            long   t0     = System.currentTimeMillis();
            byte[] buffer = new byte[1024 * 1024];
            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {
                Path newFile = destDir.resolve(zipEntry.getName());
                if (!newFile.toFile().getCanonicalPath().startsWith(destDir.toFile().getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    // fix for Windows-created archives
                    Files.createDirectories(newFile.getParent());

                    // write file content
                    try (OutputStream os = Files.newOutputStream(newFile)) {
                        for (int len; (len = zis.read(buffer)) > 0; ) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
            }
            zis.closeEntry();
            try (Stream<Path> pathStream = Files.walk(destDir)) {
                long numFiles = pathStream.filter(Files::isRegularFile).count();
                LOGGER.info("+ mvg-mps: unzipping MPS {} gave {} files, took {} ms, at {}", ext.getVersion(), numFiles, System.currentTimeMillis() - t0, destDir);
            }
        } catch (IOException e) {
            throw new GradleException("could not unzip MPS zip: " + fileZip, e);
        }
    }

    private void makeJarIndex() {
        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            // determine the jars and index them by their code name:
            Map<String, List<Path>> coreNameToPathMap = pathStream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .collect(Collectors.groupingBy(p -> p.getFileName().toString().replaceAll("[.]jar$", "")));

            // find coreNames that have just one entry under 'lib', replace them unambigously when found:
            new ArrayList<>(coreNameToPathMap.keySet()).forEach(coreName -> {
                List<Path> paths = coreNameToPathMap.get(coreName);
                if (paths.size() > 1) {
                    Map<String, List<Path>> subtreeMap = paths.stream().collect(Collectors.groupingBy(path -> rel(path).getName(0).toString()));
                    List<Path>              inLibs     = subtreeMap.get("lib");
                    if (inLibs != null && inLibs.size() == 1) {
                        if (LOGGER.isInfoEnabled()) {
                            Path       inLibRel  = rel(inLibs.getFirst());
                            List<Path> othersRel = paths.stream().map(this::rel).filter(p -> !p.equals(inLibRel)).collect(Collectors.toList());
                            LOGGER.info("+ mvg-mps: ambiguous jar-name resolved: [{}]={} (ignoring: {})", coreName, inLibRel, othersRel);
                        }
                        // overrule this one with just the lib entry:
                        coreNameToPathMap.put(coreName, inLibs);
                    }
                }
            });

            // fill the 'jarIndex' and 'ambiguousIndex' index mapss according to what we found:
            coreNameToPathMap.forEach((coreName, paths) -> {
                boolean ambigous = 1 < paths.size();
                paths.forEach(path -> {
                    jarIndex.put(rel(path).toString(), path);
                    if (!ambigous) {
                        jarIndex.put(coreName, path);
                        jarIndex.put(coreName + ".jar", path);
                    }
                });
                if (ambigous) {
                    LOGGER.info("+ mvg-mps: ambiguous jar-names found: {}", paths);
                    ambiguousIndex.put(coreName, paths);
                    ambiguousIndex.put(coreName + ".jar", paths);
                }
            });
        } catch (IOException e) {
            throw new GradleException("could not index the MPS install dir at " + rootPath, e);
        }
    }

    @NotNull
    private Path rel(Path path) {
        return rootPath.relativize(path);
    }
}
