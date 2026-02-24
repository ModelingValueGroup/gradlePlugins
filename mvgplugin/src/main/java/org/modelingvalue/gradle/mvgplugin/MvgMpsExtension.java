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

import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.MPS_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.NOW_STAMP;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION_MPS;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.InfoGradle.selectMasterDevelopElse;

import java.io.File;
import java.nio.file.Path;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class MvgMpsExtension {
    private final static String MPS_DOWNLOAD_URL_TEMPLATE = "https://download.jetbrains.com/mps/%s/MPS-%s.zip";
    private final static String MPS_ROOT_DIR_TEMPLATE     = "MPS %s"; // this name is dictated by JetBrains in their zip file setup
    private final static String MPS_DOWNLOAD_DIR_TEMPLATE = "MPS-%s";
    private final static Path   MPS_CACHE_DIR             = Path.of(System.getProperty("user.home"), ".gradle", "caches", "mps-downloads");

    public static MvgMpsExtension make(Gradle gradle) {
        return gradle.getRootProject().getExtensions().create(MPS_TASK_NAME, MvgMpsExtension.class, gradle);
    }

    private final Gradle  gradle;
    private       boolean mpsVersionSet;
    private       String  mpsVersion;

    public MvgMpsExtension(Gradle gradle) {
        this.gradle = gradle;
    }

    public synchronized String getVersion() {
        if (!mpsVersionSet) {
            mpsVersionSet = true;
            mpsVersion = getGradleDotProperties().getProp(PROP_NAME_VERSION_MPS, "0.0.1");
        }
        return mpsVersion;
    }

    public String getVersionExtra() {
        return selectMasterDevelopElse("", "EAP", "DEV");
    }

    public String getVersionStamp() {
        return selectMasterDevelopElse("", NOW_STAMP, NOW_STAMP);
    }

    public File getMpsDownloadDir() {
        return new File(gradle.getRootProject().getLayout().getBuildDirectory().get().getAsFile(), String.format(MPS_DOWNLOAD_DIR_TEMPLATE, getVersion()));
    }

    public String getMpsDownloadUrl() {
        return String.format(MPS_DOWNLOAD_URL_TEMPLATE, getMajorMpsVersion(), getVersion());
    }

    public File getMpsInstallDir() {
        return new File(getMpsDownloadDir(), String.format(MPS_ROOT_DIR_TEMPLATE, getMajorMpsVersion()));
    }

    public Path getMpsCacheFile() {
        return MPS_CACHE_DIR.resolve(String.format("MPS-%s.zip", getVersion()));
    }

    @NotNull
    private String getMajorMpsVersion() {
        String version = getVersion();
        if (version == null) {
            throw new GradleException("you need to set the MPS version in " + GRADLE_PROPERTIES_FILE + ": example: " + PROP_NAME_VERSION_MPS + "=2020.3");
        }
        return version.replaceAll("([0-9]+[.][0-9]+).*", "$1");
    }
}
