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

import static org.modelingvalue.gradle.mvgplugin.GradleDotProperties.getGradleDotProperties;
import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;
import static org.modelingvalue.gradle.mvgplugin.Info.MPS_TASK_NAME;
import static org.modelingvalue.gradle.mvgplugin.Info.NOW_STAMP;
import static org.modelingvalue.gradle.mvgplugin.Info.PROP_NAME_VERSION_MPS;
import static org.modelingvalue.gradle.mvgplugin.Info.selectMasterDevelopElse;

import java.io.File;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.extensibility.DefaultConvention;
import org.jetbrains.annotations.NotNull;

public class MvgMpsExtension {
    private final static String MPS_DOWNLOAD_URL_TEMPLATE = "http://download.jetbrains.com/mps/%s/MPS-%s.zip";
    private final static String MPS_ROOT_DIR_TEMPLATE     = "MPS %s"; // this name is dictated by JetBrains in their zip file setup
    private final static String MPS_DOWNLOAD_DIR_TEMPLATE = "MPS-%s";

    public static MvgMpsExtension make(Gradle gradle) {
        return ((DefaultConvention) gradle.getRootProject().getExtensions()).create(MPS_TASK_NAME, MvgMpsExtension.class, gradle);
    }

    private final Gradle gradle;

    public MvgMpsExtension(Gradle gradle) {
        this.gradle = gradle;
    }

    public String getVersion() {
        return getGradleDotProperties().getProp(PROP_NAME_VERSION_MPS, "0.0.1");
    }

    public String getVersionExtra() {
        return selectMasterDevelopElse(gradle, "", "beta", "alpha");
    }

    public String getVersionStamp() {
        return selectMasterDevelopElse(gradle, "", NOW_STAMP, NOW_STAMP);
    }

    public File getMpsDownloadDir() {
        return new File(gradle.getRootProject().getBuildDir(), String.format(MPS_DOWNLOAD_DIR_TEMPLATE, getVersion()));
    }

    public String getMpsDownloadUrl() {
        return String.format(MPS_DOWNLOAD_URL_TEMPLATE, getMajorMpsVersion(), getVersion());
    }

    public File getMpsInstallDir() {
        return new File(getMpsDownloadDir(), String.format(MPS_ROOT_DIR_TEMPLATE, getMajorMpsVersion()));
    }

    @NotNull
    private String getMajorMpsVersion() {
        String version = getGradleDotProperties().getProp(PROP_NAME_VERSION_MPS, "0.0.1");
        if (version == null) {
            throw new GradleException("you need to set the MPS version in " + GRADLE_PROPERTIES_FILE + ": " + PROP_NAME_VERSION_MPS + "=2020.3");
        }
        return version.replaceAll("([0-9]+[.][0-9]+).*", "$1");
    }
}
