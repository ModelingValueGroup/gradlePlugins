plugins {
    id("~myPackage~")
    `java-library`
    `maven-publish`
}
dependencies {
    implementation("demo-lib:lib:3.0.52-BRANCHED")
    implementation("org.modelingvalue:sync-proxy:0.0.0-BRANCHED")

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
publishing {
    publications {
        create<MavenPublication>("test-publication") {
            from(components["java"])

        }
    }
}
