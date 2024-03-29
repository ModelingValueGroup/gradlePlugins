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

import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.modelingvalue.gradle.mvgplugin.Info.JETBRAINS_TOKEN;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MODELING_VALUE_GROUP;
import static org.modelingvalue.gradle.mvgplugin.Info.UPLOADER_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.selectMasterDevelopElse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.extensibility.DefaultConvention;

public class MvgUploader {

    private final Extension ext;

    public MvgUploader(Gradle gradle) {
        ext = Extension.make(gradle);
        gradle.getRootProject().getTasks().register(UPLOADER_TASK_NAME, this::setup);
    }

    public static class Extension {
        public static Extension make(Gradle gradle) {
            Project project = gradle.getRootProject();
            return ((DefaultConvention) project.getExtensions()).create(UPLOADER_TASK_NAME, Extension.class, gradle);
        }

        public Gradle gradle;
        public String channel;
        public String hubToken;
        public String pluginId;
        public String zipFile;

        public Extension(Gradle gradle) {
            this.gradle = gradle;
            channel = getDefaultChannel();
            hubToken = JETBRAINS_TOKEN;
            LOGGER.info("+ mvg: default channel selected by uploader: {}", channel);
        }

        public String getDefaultChannel() {
            return selectMasterDevelopElse("stable", "EAP", BranchParameterNames.get("channel", "DEV"));
        }

        public Path getZipFile() {
            Path path = null;
            if (zipFile != null) {
                path = Paths.get(zipFile).toAbsolutePath();
                LOGGER.info("+ mvg: upload file found at {} as indicated by {} ext", path, UPLOADER_TASK_NAME);
            } else {
                Path artiDir = gradle.getRootProject().getBuildDir().toPath().resolve("artifacts");
                if (!Files.isDirectory(artiDir)) {
                    LOGGER.info("+ mvg: artifacts dir not found: {}", artiDir.toAbsolutePath());
                } else {
                    try {
                        List<Path> zipFiles = Files.list(artiDir)
                                .filter(Files::isDirectory)
                                .map(d -> d.resolve(d.getFileName().toString() + ".zip"))
                                .filter(Files::isRegularFile)
                                .collect(Collectors.toList());
                        if (zipFiles.isEmpty()) {
                            LOGGER.info("+ mvg: no zip files found in artifacts dir: {}", artiDir.toAbsolutePath());
                        } else if (1 < zipFiles.size()) {
                            LOGGER.info("+ mvg: too many ({}) zip files found in artifacts dir: {}", zipFiles.size(), artiDir.toAbsolutePath());
                        } else {
                            path = zipFiles.get(0);
                        }
                    } catch (IOException e) {
                        LOGGER.info("+ mvg: can not list artifact dir: {} ({})", artiDir.toAbsolutePath(), e.getMessage());
                    }
                }
            }
            return path;
        }
    }

    private void setup(Task task) {
        task.setGroup(MODELING_VALUE_GROUP);
        task.setDescription("upload a plugin to jetbrains");
        task.doLast(s -> execute());
    }

    private void execute() {
        LOGGER.info("+ mvg: execute {} task", UPLOADER_TASK_NAME);

        String hubToken = ext.hubToken;
        String pluginId = ext.pluginId;
        String channel  = ext.channel;
        Path   zipFile  = ext.getZipFile();

        if (zipFile == null || !Files.isRegularFile(zipFile)) {
            throw new GradleException("the selected plugin upload zipfile can not be identified: " + zipFile);
        }

        LOGGER.info("+ mvg: uploading plugin {} to channel {} from file {}", pluginId, channel, zipFile);

        if ("DRY".equals(pluginId) || "DRY".equals(hubToken)) {
            LOGGER.warn("+ mvg: DRY run: upload skipped");
        } else if (channel == null || pluginId == null || hubToken == null) {
            LOGGER.warn("+ mvg: no channel/pluginId/hubToken ({}/{}/{}): upload skipped", channel, pluginId, Util.hide(hubToken));
        } else {
            uploadToJetBrains(channel, hubToken, pluginId, zipFile);
        }
    }

    public static void uploadToJetBrains(String channel, String hubtoken, String pluginid, Path file) {
        try {
            HttpPost request = new HttpPost(Info.JETBRAINS_UPLOAD_URL);
            request.setHeader("Authorization", "Bearer " + hubtoken);
            request.setEntity(
                    MultipartEntityBuilder.create()
                            .addPart("pluginId", new StringBody(pluginid, TEXT_PLAIN))
                            .addPart("channel", new StringBody(channel, TEXT_PLAIN))
                            .addPart("file", new FileBody(file.toFile()))
                            .build()
            );

            HttpClient   httpClient = HttpClientBuilder.create().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
            HttpResponse response   = httpClient.execute(request);
            String       answer     = EntityUtils.toString(response.getEntity());

            LOGGER.info("+ mvg: upload plugin to JetBrains returned: {}", answer);

            if (!answer.startsWith("{") || !answer.endsWith("}")) {
                throw new GradleException("plugin upload returned no json object: " + answer);
            }
        } catch (IOException e) {
            throw new GradleException("plugin upload failed", e);
        }
    }
}
