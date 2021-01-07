val VERSION: String by project
val GROUP: String by project

group = GROUP
version = VERSION

plugins {
    `java-library`
    `maven-publish`
}
dependencies {
    implementation("demo-lib:lib:3.0.52-BRANCHED")
}
