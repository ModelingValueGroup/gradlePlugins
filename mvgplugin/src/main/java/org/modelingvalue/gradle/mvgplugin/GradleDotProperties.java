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

import static org.modelingvalue.gradle.mvgplugin.Info.GRADLE_PROPERTIES_FILE;

import java.io.File;

import org.gradle.api.invocation.Gradle;

public class GradleDotProperties {
    private static final File          USER_PROP_FILE = new File(new File(Util.getSystemProperty("user.home"), ".gradle"), GRADLE_PROPERTIES_FILE);
    private static final DotProperties userHomeProps  = new DotProperties(USER_PROP_FILE);
    private static       DotProperties instance       = userHomeProps;

    public static void setGradleDotProperties(Gradle gradle) {
        File dir = gradle.getRootProject().getRootDir();
        instance = new DotProperties(userHomeProps, new File(dir, GRADLE_PROPERTIES_FILE));
    }

    public static DotProperties getGradleDotProperties() {
        return instance;
    }
}
