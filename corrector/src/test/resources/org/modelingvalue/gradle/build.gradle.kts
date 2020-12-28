import org.gradle.api.artifacts.component.ModuleComponentSelector

project.version = "0.0.1"
plugins {
    java
    id("<my-package>")
}
<myExtension> {
    addTextFileExtension("pruuperties")
}
dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    implementation("demo-lib:lib:3.0.2-BRANCH")
}
repositories {
    mavenCentral()
    mavenLocal()
}