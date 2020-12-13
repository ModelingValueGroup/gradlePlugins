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

//sourceSets.main {
//    resources.includes.add("**/*")
//}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
gradlePlugin {
    plugins {
        create("mvgGreetings") {
            id = "org.modelingvalue.gradle.greeting"
            implementationClass = "org.modelingvalue.gradle.GradlePluginsPlugin"
            displayName = "MVG Greeting plugin"
            version = "0.1"
        }
    }
}

pluginBundle {
    // These settings are set for the whole plugin bundle
    website = "http://www.modelingvalue.org/"
    vcsUrl = "https://github.com/ModelingValueGroup/gradlePlugins"

    // tags and description can be set for the whole bundle here, but can also
    // be set / overridden in the config for specific plugins
    description = "MVG gradle plugins"

    // The plugins block can contain multiple plugin entries.
    //
    // The name for each plugin block below (greetingsPlugin, goodbyePlugin)
    // does not affect the plugin configuration, but they need to be unique
    // for each plugin.

    // Plugin config blocks can set the id, displayName, version, description
    // and tags for each plugin.

    // id and displayName are mandatory.
    // If no version is set, the project version will be used.
    // If no tags or description are set, the tags or description from the
    // pluginBundle block will be used, but they must be set in one of the
    // two places.

    (plugins) {
        "mvgGreetings"{
            tags = listOf("mvg", "demo")
        }
    }
}
