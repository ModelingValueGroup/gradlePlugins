import java.io.File
import java.util.concurrent.TimeUnit

plugins {
    id("~myPackage~")
    `java-library`
    `maven-publish`
}
dependencies {
    implementation("demo-lib:lib:3.1.14-BRANCHED")
    implementation("org.modelingvalue:sync-proxy:2.0.2-BRANCHED")

    implementation(mpsJar("mps-core"))
    implementation(mpsJar("closures.runtime.jar"))
    implementation(mpsJar("languages/editor/jetbrains.mps.editing.runtime.jar"))
}
`~myMvgCorrectorExtension~` {
    addTextFileExtension("pruuperties")
    addBashFileExclude("unused")
    forceEolCorrection = true
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

//
// This is a bit of a hassle... let me explain:
//   This is a test, so publishing is also only done as a test
//   Publishing to the snapshot repo is no problem, versions will be unique
//   But pubishing from master will always try to publish version 0.0.4 (due to how the test is written
//   What we do below is disable publishing when the branch is master.
//
//   This implies that this aspect of the plugin is unfortunately NOT tested
//
fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 60,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
    .directory(workingDir)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start()
    .apply { waitFor(timeoutAmount, timeoutUnit) }
    .run {
        val error = errorStream.bufferedReader().readText().trim()
        if (error.isNotEmpty()) {
            throw Exception(error)
        }
        inputStream.bufferedReader().readText().trim()
    }

val branch = "git rev-parse --abbrev-ref HEAD".runCommand(workingDir = rootDir)
println("### gradle build script found branch=${branch}")
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        !branch.equals("master")
    }
}