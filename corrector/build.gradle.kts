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

val VERSION: String by project
val GROUP: String by project
val COMPANY: String by project
val ARTIFACT: String by project

val corrector_name: String by project
val corrector_id: String by project
val corrector_version: String? by project
val corrector_class: String by project
val corrector_displayname: String by project

group = GROUP
version = VERSION

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.12.0"
}

repositories {
    jcenter()
    gradlePluginPortal()
}

tasks.test {
    useJUnitPlatform()
    systemProperty("ALLREP_TOKEN", "DRY")
    testLogging.showStandardStreams = true
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.1")
    implementation("com.gradle:gradle-enterprise-gradle-plugin:3.5")
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins.create(corrector_name) {
        id = corrector_id
        implementationClass = corrector_class
        displayName = corrector_displayname
        version = corrector_version ?: VERSION
    }
}

pluginBundle {
    website = "http://www.modelingvalue.org/"
    vcsUrl = "https://github.com/$COMPANY/$ARTIFACT"
    description = "MVG gradle plugins"
    (plugins) {
        corrector_name {
            tags = listOf("mvg")
        }
    }
}
