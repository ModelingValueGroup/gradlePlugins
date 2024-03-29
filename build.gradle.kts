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

defaultTasks("mvgCorrector", "test", "publishPlugins", "mvgTagger")

plugins {
    id("org.modelingvalue.gradle.mvgplugin") version ("1.0.7")
}
mvgcorrector {
    addHeaderFileExclude("mvgplugin/src/test/resources/.*")
    addEolFileExclude("mvgplugin/src/test/resources/.*")
    addBashFileExclude(".*/bashProduced.*")
}

// for gradle debugging:
tasks.register("task-tree") {
    doLast {
        getAllTasks(true).forEach {
            System.err.println("  > " + it.key)
            it.value.forEach {
                System.err.println("       = " + it)

                it.dependsOn.forEach {
                    if (it is TaskDependency) {
                        it.getDependencies(tasks.named("task-tree").get()).forEach {
                            System.err.println("                                - " + it)
                        }
                    } else {
                        System.err.println("                                - " + it)
                    }
                }
            }
        }
    }
}
