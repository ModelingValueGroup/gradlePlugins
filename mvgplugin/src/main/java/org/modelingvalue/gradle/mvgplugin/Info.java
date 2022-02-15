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

import static org.modelingvalue.gradle.mvgplugin.Util.envOrProp;
import static org.modelingvalue.gradle.mvgplugin.Util.envOrPropBoolean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public interface Info {
    String       MODELING_VALUE_GROUP         = "ModelingValueGroup";
    String       MVG                          = "mvg";
    String       PLUGIN_PACKAGE_NAME          = MvgPlugin.class.getPackageName();
    String       PLUGIN_CLASS_NAME            = MvgPlugin.class.getName();
    String       PLUGIN_NAME                  = MvgPlugin.class.getSimpleName().toLowerCase(Locale.ROOT);
    String       CORRECTOR_TASK_NAME          = MvgCorrector.class.getSimpleName().toLowerCase(Locale.ROOT);
    String       UPLOADER_TASK_NAME           = MvgUploader.class.getSimpleName().toLowerCase(Locale.ROOT);
    String       TAG_TASK_NAME                = MvgTagger.class.getSimpleName().toLowerCase(Locale.ROOT);
    String       MPS_TASK_NAME                = MvgMps.class.getSimpleName().toLowerCase(Locale.ROOT);
    Logger       LOGGER                       = new TimedLogger(Logging.getLogger(PLUGIN_NAME));
    String       GRADLE_PROPERTIES_FILE       = "gradle.properties";
    String       PROP_NAME_VERSION            = "version";
    String       PROP_NAME_GROUP              = "group";
    String       PROP_NAME_VERSION_JAVA       = "version_java";
    String       PROP_NAME_VERSION_MPS        = "version_mps";
    String       PROP_NAME_PLUGINS_MPS        = "plugins_mps";
    String       PROP_NAME_ALLREP_TOKEN       = "ALLREP_TOKEN";
    String       PROP_NAME_JETBRAINS_TOKEN    = "JETBRAINS_PUBLISH_TOKEN";
    String       PROP_NAME_GITHUB_WORKFLOW    = "GITHUB_WORKFLOW";
    String       PROP_NAME_CI                 = "CI";
    String       PROP_NAME_TESTING            = "TESTING";
    //
    String       NOW_STAMP                    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmm"));
    //
    boolean      CI                           = envOrPropBoolean(PROP_NAME_CI);
    boolean      TESTING                      = envOrPropBoolean(PROP_NAME_TESTING);
    String       ALLREP_TOKEN                 = envOrProp(PROP_NAME_ALLREP_TOKEN, "notset");
    String       JETBRAINS_TOKEN              = envOrProp(PROP_NAME_JETBRAINS_TOKEN, "notset");
    String       GITHUB_WORKFLOW              = envOrProp(PROP_NAME_GITHUB_WORKFLOW, null);
    String       MASTER_CHANNEL               = "";
    String       DEVELOP_CHANNEL              = "eap";
    String       OTHER_CHANNEL                = "dev";
    String       MASTER_BRANCH                = "master";
    String       DEVELOP_BRANCH               = "develop";
    String       DEFAULT_BRANCH               = "can-not-determine-branch";
    String       GIT_CONFIG_FILE              = ".git/config";
    String       GIT_HEAD_FILE                = ".git/HEAD";
    String       GIT_HEAD_FILE_START          = "ref: refs/heads/";
    String       NO_CI_GUARD                  = "!contains(github.event.head_commit.message, '[no-ci]')";
    String       MIN_TEST_HEAP_SIZE           = "2g";
    String       JAVA_VERSION                 = "11";
    String       JUNIT_VERSION                = "5.8.2";
    String       JUNIT_GROUP_ID               = "org.junit.jupiter";
    List<String> JUNIT_IMPLEMENTATION_DEPS    = List.of(
            JUNIT_GROUP_ID + ":junit-jupiter-api:" + JUNIT_VERSION,
            JUNIT_GROUP_ID + ":junit-jupiter-params:" + JUNIT_VERSION
    );
    List<String> JUNIT_RUNTIMEONLY_DEPS       = List.of(
            JUNIT_GROUP_ID + ":junit-jupiter-engine:" + JUNIT_VERSION
    );
    //
    String       PLUGIN_META_URL              = "https://plugins.gradle.org/m2/gradle/plugin/" + MvgPlugin.class.getPackageName().replace('.', '/') + "/maven-metadata.xml";
    String       JETBRAINS_UPLOAD_URL         = "https://plugins.jetbrains.com/plugin/uploadPlugin";
    String       MVG_MAVEN_REPO_BASE_URL      = "https://maven.pkg.github.com/" + MODELING_VALUE_GROUP + "/";
    String       MVG_REPO_BASE_URL            = "https://github.com/" + MODELING_VALUE_GROUP + "/";
    String       PACKAGES_SNAPSHOTS_REPO_NAME = "packages-snapshots";
    String       MVG_DEPENDENCIES_REPO_NAME   = "dependencies";
    String       PACKAGES_SNAPSHOTS_REPO      = MVG_REPO_BASE_URL + PACKAGES_SNAPSHOTS_REPO_NAME + ".git";
    String       MVG_DEPENDENCIES_REPO        = MVG_REPO_BASE_URL + MVG_DEPENDENCIES_REPO_NAME + ".git";
    String       HOSTNAME                     = Util.getHostname();
    boolean      IS_WINDOWS                   = Util.isWindows();
}

