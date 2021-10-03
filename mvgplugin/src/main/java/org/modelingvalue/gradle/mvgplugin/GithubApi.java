package org.modelingvalue.gradle.mvgplugin;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;

public class GithubApi {
    public static final String API                 = "https://api.github.com";
    public static final String OWNER               = "ModelingValueGroup";
    public static final String JSON_FORMAT         = "application/vnd.github.v3+json";
    public static final String DISPATCH_ENTRYPOINT = API + "/repos/%s/%s/actions/workflows/%s/dispatches";
    public static final String REF_IN_JSON_FORMAT  = "{\"ref\":\"%s\"}";

    /**
     * mimics the following curl call:
     * <pre>
     *   curl -v \
     *      'https://api.github.com/repos/ModelingValueGroup/immutable-collections/actions/workflows/build.yaml/dispatches' \
     *      -H "Authorization: token <token>" \
     *      -H "Accept: application/vnd.github.v3+json" \
     *      -X POST \
     *      -d '{"ref":"develop"}'
     * </pre>
     */
    public static String triggerWorkflow(String repo, String workflowFilename, String branch, Consumer<String> errorHandler) throws IOException {
        URL                url  = new URL(String.format(DISPATCH_ENTRYPOINT, OWNER, repo, workflowFilename));
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "token " + Info.ALLREP_TOKEN);
        conn.setRequestProperty("Accept", JSON_FORMAT);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write((String.format(REF_IN_JSON_FORMAT, branch)).getBytes());
        os.flush();
        conn.connect();
        try (InputStream err = conn.getErrorStream()) {
            if (err != null) {
                String msg = new String(err.readAllBytes(), UTF_8);
                if (!msg.isEmpty() && errorHandler != null) {
                    errorHandler.accept(msg);
                }
            }
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), UTF_8);
            }
        }
    }
}
