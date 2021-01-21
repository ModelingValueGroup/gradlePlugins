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
    implementation(tomtomtom("demo-lib:lib:3.0.52-BRANCHED"))
}
