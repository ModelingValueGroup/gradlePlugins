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

val mvgplugin_name: String by project
val mvgplugin_id: String by project
val mvgplugin_version: String? by project
val mvgplugin_class: String by project
val mvgplugin_displayname: String by project

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.20.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

dependencies {
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.1")
    implementation("com.gradle:gradle-enterprise-gradle-plugin:3.8")
    implementation("com.thoughtworks.xstream:xstream:1.4.18")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
    implementation("org.codehaus.jettison:jettison:1.4.1")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.20")
    implementation("org.modelingvalue:mvg-json:1.6.3-BRANCHED")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2") // when you change this version: also bump it in Info.java!
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

gradlePlugin {
    plugins.create(mvgplugin_name) {
        id = mvgplugin_id
        implementationClass = mvgplugin_class
        displayName = mvgplugin_displayname
        version = mvgplugin_version ?: version
    }
}

pluginBundle {
    website = "http://www.modelingvalue.org/"
    vcsUrl = "https://github.com/ModelingValueGroup/gradlePlugins"
    description = "MVG gradle plugins"
    (plugins) {
        mvgplugin_name {
            tags = listOf("mvg")
        }
    }
}

// still needed?:
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
tasks.publishPlugins {
    enabled = "true" == System.getenv("CI")
}
