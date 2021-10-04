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

import java.util.HashMap;
import java.util.Map;

import org.gradle.api.GradleException;

public class BranchParameterNames {
    //
    // inner DSL for branch name:
    //      some_text@a=aaa;b=bbb;c=ccc
    //
    private static BranchParameterNames instance;

    public static void init() {
        init(InfoGradle.getBranch());
    }

    public static void init(String branchName) { // separated out for testing purposes
        instance = new BranchParameterNames(branchName);
    }

    public static String get(String name, String def) {
        if (instance == null) {
            throw new GradleException("BranchParameterNames not yet inited while trying to get '" + name + "'");
        }
        return instance.get_(name, def);
    }

    private final Map<String, String> mapping = new HashMap<>();

    private BranchParameterNames(String branch) {
        if (branch.contains("@")) {
            for (String kv : branch.replaceAll("[^@]*@", "").split(";")) {
                String k = kv.replaceFirst("=.*", "");
                String v = kv.replaceFirst("[^=]*=?", "");
                mapping.putIfAbsent(k, v);
            }
        }
    }

    private String get_(String name, String def) {
        String s = mapping.get(name);
        return s == null ? def : s;
    }
}
