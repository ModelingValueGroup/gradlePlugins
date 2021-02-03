plugins {
    id("~myPackage~")
    `java-library`
    `maven-publish`
}
dependencies {
    implementation("demo-lib:lib:3.0.52-BRANCHED")

    implementation(mpsJar("mps-core"))
    implementation(mpsJar("closures.runtime.jar"))
    implementation(mpsJar("languages/editor/jetbrains.mps.editing.runtime.jar"))
}
`~myMvgCorrectorExtension~` {
    addTextFileExtension("pruuperties")
}
`~myMvgUploaderExtension~` {
    pluginId = "DRY"
    hubToken = "DRY"
}