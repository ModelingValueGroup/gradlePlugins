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

package org.modelingvalue.gradle;

import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginBean;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginId;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginRepository;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginXmlId;

public class JetBrainsPluginRepoTest {
    @Test
    void listDclarePluginVersions() {
        JetBrainsPluginRepoRestApi j = new JetBrainsPluginRepoRestApi();
        PluginBean                 p = j.getPluginByXmlId(new PluginXmlId("DclareForMPS"));
        PluginId                   id = p.id;

        PluginRepository           rep1 = j.listPlugins(null, null, id);
        rep1.categories.stream()
                .flatMap(c->c.plugins.stream())
                .forEach(pp-> System.err.printf("- %-20s  %-20s ... %s\n",pp.version,pp.ideaVersion.sinceBuild,pp.ideaVersion.untilBuild));

        PluginRepository           rep2 = j.listPlugins(null, "eap", id);
        rep2.categories.stream()
                .flatMap(c->c.plugins.stream())
                .forEach(pp-> System.err.printf("- %-20s  %-20s ... %s\n",pp.version,pp.ideaVersion.sinceBuild,pp.ideaVersion.untilBuild));
    }
}
