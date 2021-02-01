plugins {
    id("~myPackage~")
    `java-library`
    `maven-publish`
}
`~myMvgCorrectorExtension~` {
    addTextFileExtension("pruuperties")
}
dependencies {
    implementation("demo-lib:lib:3.0.52-BRANCHED")

    implementation(mpsJar("mps-core"))
    implementation(mpsJar("closures.runtime.jar"))
    implementation(mpsJar("languages/editor/jetbrains.mps.editing.runtime.jar"))
}
