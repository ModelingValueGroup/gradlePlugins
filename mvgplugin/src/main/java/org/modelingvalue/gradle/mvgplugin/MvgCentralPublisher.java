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

import static org.modelingvalue.gradle.mvgplugin.Info.CENTRAL_PASSWORD;
import static org.modelingvalue.gradle.mvgplugin.Info.CENTRAL_PUBLISHER_API_URL;
import static org.modelingvalue.gradle.mvgplugin.Info.CENTRAL_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.CENTRAL_USERNAME;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MODELING_VALUE_GROUP;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_REPO_BASE_URL;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_SIGNING_KEY;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_SIGNING_PASSPHRASE;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMasterBranch;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.isMvgCI_orTesting;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.signing.SigningExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MvgCentralPublisher {
    public static final String       CENTRAL_STAGING_REPO_NAME = "MvgCentralStaging";
    public static final String       CENTRAL_STAGING_DIR       = "central-staging";
    public static final String       CENTRAL_BUNDLE_FILE       = "central-bundle.zip";
    public static final long         POLL_TIMEOUT_MS           = 15 * 60 * 1000;
    public static final long         POLL_INTERVAL_MS          = 10 * 1000;
    public static final List<String> CHECKSUM_EXTENSIONS       = List.of(".md5", ".sha1", ".sha256", ".sha512");

    private final Gradle gradle;
    private final Path   stagingDir;
    private final Path   bundleFile;

    public MvgCentralPublisher(Gradle gradle) {
        this.gradle = gradle;
        Path buildDir = gradle.getRootProject().getLayout().getBuildDirectory().get().getAsFile().toPath();
        stagingDir = buildDir.resolve(CENTRAL_STAGING_DIR);
        bundleFile = buildDir.resolve(CENTRAL_BUNDLE_FILE);

        boolean shouldPublish = isMvgCI_orTesting() && isMasterBranch();
        boolean haveCreds     = CENTRAL_USERNAME != null && CENTRAL_PASSWORD != null && MVG_SIGNING_KEY != null && MVG_SIGNING_PASSPHRASE != null;
        LOGGER.info("+ mvg-central: creating MvgCentralPublisher (CI={} master={} creds={})", isMvgCI_orTesting(), isMasterBranch(), haveCreds);
        if (shouldPublish && !haveCreds) {
            LOGGER.warn("+ mvg-central: maven central publishing SKIPPED, missing: {}", missingCredNames());
        }
        if (shouldPublish && haveCreds) {
            deleteStagingDir(); // a stale tree from an earlier run would end up in the bundle and be rejected as duplicate
            TaskProvider<Task> centralTask = gradle.getRootProject().getTasks().register(CENTRAL_TASK_NAME, this::setup);
            gradle.afterProject(p -> {
                PublishingExtension publishing = (PublishingExtension) p.getExtensions().findByName("publishing");
                if (publishing != null && !publishing.getPublications().isEmpty()) {
                    signPublications(p, publishing);
                    completePoms(p, publishing);
                    addStagingRepo(publishing);
                    p.getTasks().matching(t -> t.getName().equals("publish")).configureEach(t -> t.finalizedBy(centralTask));
                }
            });
        }
    }

    private void deleteStagingDir() {
        if (Files.isDirectory(stagingDir)) {
            LOGGER.info("+ mvg-central: deleting stale staging dir {}", stagingDir);
            try (Stream<Path> stream = Files.walk(stagingDir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            } catch (IOException e) {
                throw new GradleException("can not delete staging dir " + stagingDir, e);
            }
        }
    }

    private static String missingCredNames() {
        return Stream.of(
                CENTRAL_USERNAME == null ? Info.PROP_NAME_CENTRAL_USERNAME : null,
                CENTRAL_PASSWORD == null ? Info.PROP_NAME_CENTRAL_PASSWORD : null,
                MVG_SIGNING_KEY == null ? Info.PROP_NAME_SIGNING_KEY : null,
                MVG_SIGNING_PASSPHRASE == null ? Info.PROP_NAME_SIGNING_PASSPHRASE : null
        ).filter(n -> n != null).collect(Collectors.joining(", "));
    }

    private void signPublications(Project p, PublishingExtension publishing) {
        p.getPluginManager().apply("signing");
        SigningExtension signing = p.getExtensions().getByType(SigningExtension.class);
        signing.useInMemoryPgpKeys(MVG_SIGNING_KEY, MVG_SIGNING_PASSPHRASE);
        publishing.getPublications().all(pub -> {
            LOGGER.info("+ mvg-central: signing publication {} of project {}", pub.getName(), p.getName());
            signing.sign(pub);
        });
    }

    // maven central refuses poms without name/description/url/license/developers/scm, so complete them
    // here; name/description/url are conventions (a build script can override), the rest is fixed MVG info
    private void completePoms(Project p, PublishingExtension publishing) {
        String repoUrl = MVG_REPO_BASE_URL + (InfoGradle.getMvgRepoName() != null ? InfoGradle.getMvgRepoName() : p.getRootProject().getName());
        publishing.getPublications().withType(MavenPublication.class).all(pub -> pub.pom(pom -> {
            LOGGER.info("+ mvg-central: completing pom of publication {} of project {}", pub.getName(), p.getName());
            pom.getName().convention(pub.getArtifactId());
            pom.getDescription().convention(p.provider(() -> p.getDescription() != null ? p.getDescription() : pub.getArtifactId() + " by " + MODELING_VALUE_GROUP));
            pom.getUrl().convention(repoUrl);
            pom.licenses(ls -> ls.license(l -> {
                l.getName().set("GNU Lesser General Public License v3.0");
                l.getUrl().set("https://www.gnu.org/licenses/lgpl-3.0.html");
            }));
            pom.developers(ds -> ds.developer(d -> {
                d.getId().set(MODELING_VALUE_GROUP);
                d.getName().set("Modeling Value Group B.V.");
                d.getEmail().set("team@modelingvalue.org");
            }));
            pom.scm(scm -> {
                scm.getUrl().set(repoUrl);
                scm.getConnection().set("scm:git:" + repoUrl + ".git");
                scm.getDeveloperConnection().set("scm:git:" + repoUrl + ".git");
            });
        }));
    }

    private void addStagingRepo(PublishingExtension publishing) {
        if (publishing.getRepositories().stream().noneMatch(r -> r.getName().equals(CENTRAL_STAGING_REPO_NAME))) {
            LOGGER.info("+ mvg-central: adding staging publishing repo: {}", stagingDir);
            publishing.getRepositories().maven(m -> {
                m.setName(CENTRAL_STAGING_REPO_NAME);
                m.setUrl(stagingDir.toUri());
            });
        }
    }

    private void setup(Task task) {
        task.setGroup(MODELING_VALUE_GROUP);
        task.setDescription("bundle the staged publications and upload them to maven central");
        task.doLast(s -> execute());
    }

    private void execute() {
        List<Task> failed = gradle.getTaskGraph().getAllTasks().stream().filter(t -> t.getState().getFailure() != null).collect(Collectors.toList());
        if (!failed.isEmpty()) {
            LOGGER.warn("+ mvg-central: upload to maven central skipped because tasks failed: {}", failed.stream().map(Task::getName).collect(Collectors.joining(", ")));
            return;
        }
        if (!Files.isDirectory(stagingDir)) {
            LOGGER.warn("+ mvg-central: upload to maven central skipped, nothing was staged in {}", stagingDir);
            return;
        }
        try {
            createBundle(stagingDir, bundleFile);
            String deploymentName = gradle.getRootProject().getName() + "-" + gradle.getRootProject().getVersion();
            String deploymentId   = upload(deploymentName, bundleFile);
            pollUntilDone(deploymentId);
        } catch (IOException e) {
            throw new GradleException("maven central publish failed", e);
        }
    }

    // zips the staged maven tree into a central-portal bundle: maven-metadata files are not part of a
    // bundle, and every artifact needs md5+sha1 checksums (generated here when gradle did not write them)
    public static void createBundle(Path stagingDir, Path bundleFile) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(f -> !f.getFileName().toString().startsWith("maven-metadata"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        for (Path f : List.copyOf(files)) {
            if (isArtifact(f)) {
                for (String algo : List.of("MD5", "SHA-1")) {
                    Path checksumFile = f.resolveSibling(f.getFileName() + (algo.equals("MD5") ? ".md5" : ".sha1"));
                    if (!Files.isRegularFile(checksumFile)) {
                        Files.writeString(checksumFile, checksum(f, algo));
                        files.add(checksumFile);
                    }
                }
            }
        }
        Files.createDirectories(bundleFile.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundleFile))) {
            for (Path f : files.stream().sorted().collect(Collectors.toList())) {
                zip.putNextEntry(new ZipEntry(stagingDir.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, zip);
                zip.closeEntry();
            }
        }
        LOGGER.info("+ mvg-central: created bundle {} with {} files", bundleFile, files.size());
    }

    private static boolean isArtifact(Path f) {
        String name = f.getFileName().toString();
        return !name.endsWith(".asc") && CHECKSUM_EXTENSIONS.stream().noneMatch(name::endsWith);
    }

    public static String checksum(Path f, String algo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algo);
            digest.update(Files.readAllBytes(f));
            StringBuilder b = new StringBuilder();
            for (byte x : digest.digest()) {
                b.append(String.format("%02x", x));
            }
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("checksum algorithm missing: " + algo, e);
        }
    }

    private String upload(String deploymentName, Path bundle) throws IOException {
        String   url      = CENTRAL_PUBLISHER_API_URL + "/upload?publishingType=AUTOMATIC&name=" + URLEncoder.encode(deploymentName, StandardCharsets.UTF_8);
        HttpPost request  = new HttpPost(url);
        request.setHeader("Authorization", authHeader());
        request.setEntity(MultipartEntityBuilder.create().addPart("bundle", new FileBody(bundle.toFile())).build());

        HttpResponse response = httpClient().execute(request);
        int          status   = response.getStatusLine().getStatusCode();
        String       answer   = EntityUtils.toString(response.getEntity()).trim();
        LOGGER.info("+ mvg-central: bundle upload of {} returned: HTTP {} {}", deploymentName, status, answer);
        if (status < 200 || 300 <= status) {
            throw new GradleException("maven central bundle upload failed (HTTP " + status + "): " + answer);
        }
        return answer;
    }

    private void pollUntilDone(String deploymentId) throws IOException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            HttpPost request = new HttpPost(CENTRAL_PUBLISHER_API_URL + "/status?id=" + URLEncoder.encode(deploymentId, StandardCharsets.UTF_8));
            request.setHeader("Authorization", authHeader());
            HttpResponse response = httpClient().execute(request);
            int          status   = response.getStatusLine().getStatusCode();
            String       answer   = EntityUtils.toString(response.getEntity());
            if (status < 200 || 300 <= status) {
                throw new GradleException("maven central status check failed (HTTP " + status + "): " + answer);
            }
            JsonNode json  = new ObjectMapper().readTree(answer);
            String   state = json.path("deploymentState").asText();
            LOGGER.info("+ mvg-central: deployment {} state: {}", deploymentId, state);
            if (state.equals("PUBLISHING") || state.equals("PUBLISHED")) {
                LOGGER.info("+ mvg-central: deployment {} accepted by maven central", deploymentId);
                return;
            }
            if (state.equals("FAILED")) {
                throw new GradleException("maven central rejected the deployment: " + json.path("errors").toString());
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GradleException("interrupted while waiting for maven central validation", e);
            }
        }
        throw new GradleException("maven central validation timed out for deployment " + deploymentId);
    }

    private static String authHeader() {
        return "Bearer " + Base64.getEncoder().encodeToString((CENTRAL_USERNAME + ":" + CENTRAL_PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpClient httpClient() {
        return HttpClientBuilder.create().build();
    }
}
