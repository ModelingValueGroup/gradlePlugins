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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

public class MvgMps {
    private final static int    MAX_REDIRECTS = 10;
    private final static Object LOAD_LOCK     = new Object();

    private final    Gradle            gradle;
    private final    MvgMpsExtension   ext;
    private          boolean           mpsHasBeenLoaded;
    private volatile Map<String, File> jarIndex;
    private          Properties        mpsBuildProps;
    private          Version           mpsBuildNumber;

    public MvgMps(Gradle gradle) {
        this.gradle = gradle;
        ext = MvgMpsExtension.make(gradle);
    }

    public Object resolveMpsDependency(String dep) {
        File jar = getJarIndex().get(dep);
        if (jar == null) {
            throw new GradleException("no jar found for '" + dep + "' in " + ext.getMpsInstallDir());
        }
        //TODO:  build/test-workspace/gradlePlugins/build/MPS-2020.3/MPS 2020.3/lib/MPS-src.zip
        LOGGER.info("+ mvg-mps: dependency replaced: {} => {}", dep, jar);
        return gradle.getRootProject().files(jar);
    }

    public Properties getMpsBuildProps() {
        loadMps();
        return mpsBuildProps;
    }

    public Version getMpsBuildNumber() {
        loadMps();
        return mpsBuildNumber;
    }

    private Map<String, File> getJarIndex() {
        loadMps();
        return jarIndex;
    }

    private void loadMps() {
        synchronized (LOAD_LOCK) { // we will be careful, you never know if gradle will call us at the same time in multiple Threads
            if (!mpsHasBeenLoaded) {
                assert jarIndex == null;
                assert mpsBuildProps == null;
                assert mpsBuildNumber == null;

                downloadAndUnzip(ext);

                jarIndex = makeJarIndex(ext.getMpsInstallDir());
                mpsBuildProps = readMpsBuildProps();
                mpsBuildNumber = getBuildNumber();
                checkAntFilesAgainstMpsBuildNumber();

                LOGGER.info("+ mvg: indexed {} jars in MPS dir {}", jarIndex.size(), ext.getMpsInstallDir());
                LOGGER.info("+ mvg: loaded MPS properties, mps.build.number={}", mpsBuildNumber);

                mpsHasBeenLoaded = true;
                assert jarIndex != null;
                assert mpsBuildProps != null;
            }
        }
    }

    private void checkAntFilesAgainstMpsBuildNumber() {
        try {
            Files.walk(InfoGradle.getAbsProjectDir())
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .map(AntFileMpsVersionsExtractor::new)
                    .filter(AntFileMpsVersionsExtractor::isAntFile)
                    .filter(AntFileMpsVersionsExtractor::hasRange)
                    .forEach(a -> {
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

    private static void downloadAndUnzip(MvgMpsExtension ext) {
        Path mpsDownloadDir = ext.getMpsDownloadDir().toPath();
        Path mpsZip         = mpsDownloadDir.resolve("mps.zip");
        Path mpsInstallDir  = ext.getMpsInstallDir().toPath();

        if (!Files.isRegularFile(mpsZip)) {
            downloadMps(ext, mpsZip);
        }
        if (!Files.isDirectory(mpsInstallDir)) {
            unzip(ext, mpsZip, mpsDownloadDir);
        }
    }

    private static void downloadMps(MvgMpsExtension ext, Path mpsZip) {
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
            long numFiles = Files.walk(destDir).filter(Files::isRegularFile).count();
            LOGGER.info("+ mvg-mps: unzipping   MPS {} gave {} files, took {} ms, at {}", ext.getVersion(), numFiles, System.currentTimeMillis() - t0, destDir);
        } catch (IOException e) {
            throw new GradleException("could not unzip MPS zip: " + fileZip, e);
        }
    }

    private static Map<String, File> makeJarIndex(File rootDir) {
        Map<String, File> index = new HashMap<>();
        try {
            Path rootPath = rootDir.toPath();
            Files.walk(rootPath)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(p -> {
                        File asFile = p.toFile();
                        index.put(rootPath.relativize(p).toString(), asFile);
                        index.put(p.getFileName().toString(), asFile);
                        index.put(p.getFileName().toString().replaceAll("[.]jar$", ""), asFile);
                    });
        } catch (IOException e) {
            throw new GradleException("could not index the MPS install dir at " + rootDir, e);
        }
        return index;
    }
}
