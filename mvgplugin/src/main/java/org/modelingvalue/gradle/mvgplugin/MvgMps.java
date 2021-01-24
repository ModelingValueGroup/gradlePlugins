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

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;

public class MvgMps {
    private final static int    MAX_REDIRECTS = 10;
    private final static Object LOCK          = new Object();

    private final    Gradle            gradle;
    private final    MvgMpsExtension   ext;
    private volatile Map<String, File> jarIndex;

    public MvgMps(Gradle gradle) {
        this.gradle = gradle;
        ext = MvgMpsExtension.make(gradle);
    }

    public Object resolveMpsDependency(String dep) {
        File jar = getJarIndex().get(dep);
        if (jar == null) {
            throw new GradleException("no jar found for '" + dep + "' in " + ext.getMpsInstallDir());
        }
        LOGGER.info("+ MPS: dependency     replaced: " + dep + " => " + jar);
        return gradle.getRootProject().files(jar);
    }

    private Map<String, File> getJarIndex() {
        if (jarIndex == null) {
            synchronized (LOCK) { // we will be careful, you never know if gradle will call us at the same time in multiple Threads
                if (jarIndex == null) {
                    jarIndex = downloadUnzipAndIndex(ext);
                }
            }
        }
        return jarIndex;
    }

    private static Map<String, File> downloadUnzipAndIndex(MvgMpsExtension ext) {
        File mpsDownloadDir = ext.getMpsDownloadDir();
        File mpsZip         = new File(mpsDownloadDir, "mps.zip");
        File mpsInstallDir  = ext.getMpsInstallDir();

        if (!mpsZip.isFile()) {
            downloadMps(ext, mpsZip);
        }
        if (!mpsInstallDir.isDirectory()) {
            unzip(ext, mpsZip, mpsDownloadDir);
        }

        Map<String, File> jarIndex = makeJarIndex(mpsInstallDir);
        LOGGER.info("+ indexed {} jars in MPS dir {}", jarIndex.size(), mpsInstallDir);
        return jarIndex;
    }

    private static void downloadMps(MvgMpsExtension ext, File mpsZip) {
        long t0 = System.currentTimeMillis();
        //noinspection ResultOfMethodCallIgnored
        mpsZip.getParentFile().mkdirs();
        String url = ext.getMpsDownloadUrl();
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            for (int i = 0; i < MAX_REDIRECTS; i++) {
                String redirect = urlConnection.getHeaderField("Location");
                if (redirect == null) {
                    break;
                }
                LOGGER.info("+ MPS download redirect => {}", redirect);
                urlConnection = new URL(redirect).openConnection();
            }
            try (InputStream is = urlConnection.getInputStream()) {
                long len = new FileOutputStream(mpsZip).getChannel().transferFrom(Channels.newChannel(is), 0, Long.MAX_VALUE);
                if (len == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    mpsZip.delete();
                    throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (got empty file)");
                }
                LOGGER.info("+ read {} bytes of zip file", len);
            }
        } catch (IOException e) {
            throw new GradleException("could not download MPS " + ext.getVersion() + " from " + url + " (" + e.getMessage() + ")", e);
        }
        LOGGER.info("+ downloading MPS {} took {} ms", ext.getVersion(), System.currentTimeMillis() - t0);
    }

    private static void unzip(MvgMpsExtension ext, File fileZip, File destDir) {
        long t0 = System.currentTimeMillis();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip))) {
            byte[] buffer = new byte[1024 * 1024];
            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {
                File newFile = new File(destDir, zipEntry.getName());
                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        for (int len; (len = zis.read(buffer)) > 0; ) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
            zis.closeEntry();
        } catch (IOException e) {
            throw new GradleException("could not unzip MPS zip: " + fileZip, e);
        }
        LOGGER.info("+ unzipping   MPS {} took {} ms", ext.getVersion(), System.currentTimeMillis() - t0);
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
