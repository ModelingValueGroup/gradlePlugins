val VERSION: String by project
val GROUP: String by project

group = GROUP
version = VERSION

plugins {
    id("~my-package~")
    `java-library`
    `maven-publish`
}
~myExtension~ {
    addTextFileExtension("pruuperties")
}
dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    implementation("demo-lib:lib:3.0.52-BRANCHED")
}
publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}
