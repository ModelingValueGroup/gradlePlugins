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

import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION_MPS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

public class MvgMps {
    private final static int MAX_REDIRECTS = 10;

    private final Gradle                  gradle;
    private final MvgMpsExtension         ext;
    private final MpsPluginDownloader     mpsPluginDownloader = new MpsPluginDownloader();
    private       boolean                 mpsInstallDone;
    private       Properties              mpsBuildProps;
    private       Version                 mpsBuildNumber;
    private       Map<String, Path>       jarIndex;
    private       Map<String, List<Path>> ambiguousIndex;

    public MvgMps(Gradle gradle) {
        this.gradle = gradle;
        ext = MvgMpsExtension.make(gradle);
        if (ext.getVersion() == null) {
            LOGGER.info("+ mvg-mps: MPS not loaded because I see no {} property in the {} file", PROP_NAME_VERSION_MPS, GRADLE_PROPERTIES_FILE);
        }
    }

    private void needMpsNow() {
        if (!mpsInstallDone) {
            mpsInstallDone = true;
            if (ext.getVersion() == null) {
                throw new GradleException("no mps version detected in properties; please specify 'version_mps=2020.3' (or equivalent) in the gradle.properties file");
            }
            Path mpsZip        = ext.getMpsDownloadDir().resolve("MPS-" + ext.getVersion() + ".zip");
            Path mpsInstallDir = ext.getMpsInstallDir().toPath();

            if (!Files.isRegularFile(mpsZip)) {
                downloadMps(mpsZip);
            } else {
                LOGGER.info("+ mvg-mps: MPS zip already downloaded at {}", mpsZip);
            }
            if (!Files.isDirectory(mpsInstallDir)) {
                unzip(mpsZip, mpsInstallDir.getParent());
            }
            if (mpsBuildProps == null) {
                mpsBuildProps = readMpsBuildProps();
            }
            if (mpsBuildNumber == null) {
                mpsBuildNumber = getBuildNumber();
            }

            mpsPluginDownloader.downloadAllPlugins(mpsInstallDir, ext.getPlugins(), "MPS-" + mpsBuildNumber, InfoGradle.getChannel())
                    .forEach(zipFile -> unzip(zipFile, mpsInstallDir.resolve("plugins")));

            if (jarIndex == null || ambiguousIndex == null) {
                Map<String, List<Path>> ambiguousIndex = new HashMap<>();
                jarIndex = makeJarIndex(mpsInstallDir, ambiguousIndex);
                this.ambiguousIndex = ambiguousIndex;
                checkAntFilesAgainstMpsBuildNumber();
            }

            LOGGER.info("+ mvg-mps: indexed {} jars in MPS dir {}", jarIndex.size(), ext.getMpsInstallDir());
            LOGGER.info("+ mvg-mps: loaded MPS properties, mps.build.number={}", mpsBuildNumber);
        }
    }


    public FileCollection getFiles(String dep) {
        needMpsNow();
        Path jar = getJarIndex().get(dep);
        if (jar == null) {
            List<Path> candidates    = ambiguousIndex.get(dep);
            String     candidatesMsg = candidates == null ? "" : " (do you mean one of these: " + candidates + "?)";
            throw new GradleException("no jar found for '" + dep + "' in " + ext.getMpsInstallDir() + candidatesMsg);
        }
        //TODO:  build/test-workspace/gradlePlugins/build/MPS-2020.3/MPS 2020.3/lib/MPS-src.zip
        LOGGER.info("+ mvg-mps: dependency replaced: {} => {}", dep, jar);
        return gradle.getRootProject().files(jar.toFile().getAbsoluteFile());
    }

    public Properties getMpsBuildProps() {
        needMpsNow();
        return mpsBuildProps;
    }

    public Version getMpsBuildNumber() {
        needMpsNow();
        return mpsBuildNumber;
    }

    private Map<String, Path> getJarIndex() {
        return jarIndex;
    }

    private void checkAntFilesAgainstMpsBuildNumber() {
        try {
            Files.walk(InfoGradle.getAbsProjectDir()).filter(p -> p.getFileName().toString().endsWith(".xml")).map(AntFileMpsVersionsExtractor::new).filter(AntFileMpsVersionsExtractor::isAntFile).filter(AntFileMpsVersionsExtractor::hasRange).forEach(a -> {
                if (a.getSince() != null && mpsBuildNumber.compareTo(a.getSince()) < 0) {
                    LOGGER.warn("the MPS build number {} of MPS {} is below the range [{}...{}] mentioned in ant file: {}", mpsBuildNumber, ext.getVersion(), a.getSince(), a.getUntil(), a.getFile());
                } else if (a.getUntil() != null && 0 < mpsBuildNumber.compareTo(a.getUntil())) {
                    LOGGER.warn("the MPS build number {} of MPS {} is above the range [{}...{}] mentioned in ant file: {}", mpsBuildNumber, ext.getVersion(), a.getSince(), a.getUntil(), a.getFile());
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
            LOGGER.warn("could not read the MPS properties file at {}", propFile.toAbsolutePath());
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

    private void downloadMps(Path mpsZip) {
        long   t0  = System.currentTimeMillis();
        String url = ext.getMpsDownloadUrl();
        try {
            Files.createDirectories(mpsZip.getParent());
            URLConnection urlConnection = new URL(url).openConnection();
            for (int i = 0; i < MAX_REDIRECTS; i++) {
                String redirect = urlConnection.getHeaderField("Location");
                if (redirect == null) {
                    break;
                }
                LOGGER.info("+ mvg-mps: MPS download redirect => {}", redirect);
                urlConnection = new URL(redirect).openConnection();
            }
            try (InputStream is = urlConnection.getInputStream()) {
                long len = new FileOutputStream(mpsZip.toFile()).getChannel().transferFrom(Channels.newChannel(is), 0, Long.MAX_VALUE);
                if (len == 0) {
                    Files.deleteIfExists(mpsZip);
                    throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (got empty file)");
                }
                LOGGER.info("+ mvg-mps: downloaded {} bytes of zip file to {}", len, mpsZip);
            }
        } catch (IOException e) {
            throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (" + e.getMessage() + ")", e);
        }
        LOGGER.info("+ mvg-mps: downloading MPS {} took {} ms", ext.getVersion(), System.currentTimeMillis() - t0);
    }

    private void unzip(Path fileZip, Path destDir) {
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
            long numFiles = Files.walk(destDir).filter(Files::isRegularFile).count();
            LOGGER.info("+ mvg-mps: unzipping   MPS {} gave {} files, took {} ms, at {}", ext.getVersion(), numFiles, System.currentTimeMillis() - t0, destDir);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(fileZip);
            } catch (IOException ex) {
                // ignore
            }
            throw new GradleException("could not unzip MPS zip (file deleted): " + fileZip, e);
        }
    }

    private static Map<String, Path> makeJarIndex(Path mpsDir, Map<String, List<Path>> ambiguousIndex) {
        try {
            // determine the jars and index them by their code name:
            Map<String, List<Path>> coreNameToPathMap = Files.walk(mpsDir).filter(p -> p.getFileName().toString().endsWith(".jar")).collect(Collectors.groupingBy(p -> p.getFileName().toString().replaceAll("[.]jar$", "")));

            // find coreNames that have just one entry under 'lib', replace them unambigously when found:
            new ArrayList<>(coreNameToPathMap.keySet()).forEach(coreName -> {
                List<Path> paths = coreNameToPathMap.get(coreName);
                if (paths.size() > 1) {
                    Map<String, List<Path>> subtreeMap = paths.stream().collect(Collectors.groupingBy(path -> mpsDir.relativize(path).getName(0).toString()));
                    List<Path>              inLibs     = subtreeMap.get("lib");
                    if (inLibs != null && inLibs.size() == 1) {
                        if (LOGGER.isInfoEnabled()) {
                            Path       inLibRel  = mpsDir.relativize((inLibs.get(0)));
                            List<Path> othersRel = paths.stream().map(mpsDir::relativize).filter(p -> !p.equals(inLibRel)).collect(Collectors.toList());
                            LOGGER.info("+ mvg-mps: ambiguous jar-name resolved: [{}]={} (ignoring: {})", coreName, inLibRel, othersRel);
                        }
                        // overrule this one with just the lib entry:
                        coreNameToPathMap.put(coreName, inLibs);
                    }
                }
            });

            // fill the 'jarIndex' and 'ambiguousIndex' index mapss according to what we found:
            Map<String, Path> jarIndex = new HashMap<>();
            coreNameToPathMap.forEach((coreName, paths) -> {
                boolean ambigous = 1 < paths.size();
                paths.forEach(path -> {
                    jarIndex.put(mpsDir.relativize(path).toString(), path);
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
            assert !jarIndex.isEmpty();
            return jarIndex;
        } catch (IOException e) {
            throw new GradleException("could not index the MPS install dir at " + mpsDir.toAbsolutePath(), e);
        }
    }
}
