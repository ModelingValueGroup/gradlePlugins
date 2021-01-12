plugins {
    id("~my-package~")
    `java-library`
    `maven-publish`
}
~myExtension~ {
    addTextFileExtension("pruuperties")
}
dependencies {
    implementation("demo-lib:lib:3.0.52-BRANCHED")
}
