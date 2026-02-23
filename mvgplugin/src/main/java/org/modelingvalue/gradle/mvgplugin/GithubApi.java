//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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
    @SuppressWarnings("JavadocLinkAsPlainText")
    public static String triggerWorkflow(String repo, String workflowFilename, String branch, Consumer<String> errorHandler) throws IOException {
        URL                url  = Util.getUrl(String.format(DISPATCH_ENTRYPOINT, OWNER, repo, workflowFilename));
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
