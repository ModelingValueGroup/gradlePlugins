val VERSION: String by project
val GROUP: String by project
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
}
tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

gradlePlugin {
    plugins {
        create(corrector_name) {
            id = corrector_id
            implementationClass = corrector_class
            displayName = corrector_displayname
            version = corrector_version ?: VERSION
        }
    }
}

pluginBundle {
    website = "http://www.modelingvalue.org/"
    vcsUrl = "https://github.com/ModelingValueGroup/gradlePlugins"
    description = "MVG gradle plugins"
    (plugins) {
        corrector_name {
            tags = listOf("mvg")
        }
    }
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
