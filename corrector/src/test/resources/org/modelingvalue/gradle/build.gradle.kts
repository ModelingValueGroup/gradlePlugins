val ALLREP_TOKEN: String = System.getenv("ALLREP_TOKEN") ?: "DRY"

group = "mygroup"
version = "0.0.1"

plugins {
    java
    id("~my-package~")
    `maven-publish`
}
~myExtension~ {
    addTextFileExtension("pruuperties")
}
dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    //TOMTOMTOM
implementation("demo-lib:lib:3.0.52-BRANCHED")
}
repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://maven.pkg.github.com/ModelingValueGroup/packages")
        credentials {
            username = "" // can be anything but plugin requires it
            password = ALLREP_TOKEN
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/ModelingValueGroup/packages-snapshots")
        credentials {
            username = "" // can be anything but plugin requires it
            password = ALLREP_TOKEN
        }
    }
}
gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}
