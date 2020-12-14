group = "org.modelingvalue"

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

val correctorClass = "org.modelingvalue.gradle.corrector.MvgCorrectorPlugin" //secured in test idCheck
val correctorName = "mvgCorrector" //secured in test idCheck
val correctorId = "org.modelingvalue.gradle.corrector" //secured in test idCheck

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

gradlePlugin {
    plugins {
        create(correctorName) {
            id = correctorId
            implementationClass = correctorClass
            displayName = "MVG corrector plugin"
            version = "0.1"
        }
    }
}

pluginBundle {
    website = "http://www.modelingvalue.org/"
    vcsUrl = "https://github.com/ModelingValueGroup/gradlePlugins"
    description = "MVG gradle plugins"
    (plugins) {
        correctorName {
            tags = listOf("mvg")
        }
    }
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}