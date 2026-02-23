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

defaultTasks("test", "publishPlugins")

// TODO: Re-enable once the Gradle 9 compatible version of the plugin is published
//plugins {
//    id("org.modelingvalue.gradle.mvgplugin") version ("1.0.7")
//}
//mvgcorrector {
//    addHeaderFileExclude("mvgplugin/src/test/resources/.*")
//    addEolFileExclude("mvgplugin/src/test/resources/.*")
//    addBashFileExclude(".*/bashProduced.*")
//}

// for gradle debugging:
tasks.register("task-tree") {
    doLast {
        getAllTasks(true).forEach { task ->
            System.err.println("  > " + task.key)
            task.value.forEach { t ->
                System.err.println("       = $t")

                t.dependsOn.forEach { dep ->
                    if (dep is TaskDependency) {
                        dep.getDependencies(tasks.named("task-tree").get()).forEach { depdep ->
                            System.err.println("                                - $depdep")
                        }
                    } else {
                        System.err.println("                                - $dep")
                    }
                }
            }
        }
    }
}
