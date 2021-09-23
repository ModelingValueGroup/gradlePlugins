# Cheat-Sheet for gradle-kotlin
## MVG
#### gradle.properties
```properties
org.gradle.logging.level=info
```

#### settings.gradle.kts
```kotlin
include("subA", "subB")
plugins {
    id("com.gradle.enterprise") version ("3.5")
}
```
#### build.gradle.kts (parent project)
```kotlin
defaultTasks("mvgCorrector", "test", "publish", "mvgTagger")
plugins {
    id("org.modelingvalue.gradle.mvgplugin") version "0.4.1"
}
```
#### build.gradle.kts (java library)
```kotlin
plugins {
    `java-library`
    `maven-publish`
}
publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}
```
#### build.gradle.kts (java application)
```kotlin
plugins {
    application
    `maven-publish`
}
dependencies {
    implementation("demo-lib:lib:3.1.0-BRANCHED")
}
application {
    mainClass.set("demo.app.App")
}
```

## Tasks
#### build.gradle.kts
```kotlin
tasks.register("createTask") {
    enabled = false
    timeout.set(Duration.ofMillis(500))
    doLast {
        println("hello")
    }
}
tasks.register<Copy>("copy") {
    onlyIf { !project.hasProperty("skipCopy") } // gradle -PskipCopy ...
    description = "Copies the resource directory to the target directory."
    from("resources")
    into("target")
    include("**/*.txt", "**/*.xml", "**/*.properties")
}
```
```kotlin
tasks.register("depends") {
    dependsOn("createTask")
    doLast {
        println("world")
    }
}
```
```kotlin
val a by tasks.registering {}
val b by tasks.registering {}
a {
    dependsOn(b)
}
```
```kotlin
taskX {
    dependsOn(provider {
        tasks.filter { task -> task.name.startsWith("lib") }
    })
}
tasks.register("lib1") {}
tasks.register("lib2") {}
```
```kotlin
alwaysAfterA {
    dependsOn(A)
}
alwaysAfterA_OrNotAtAll {
    mustRunAfter(A)
}
preferablyAfterA_OrNotAtAll {
    shouldRunAfter(A)
}
```
```kotlin
abstract class CustomTask @javax.inject.Inject constructor(private val message: String) : DefaultTask() {
    @TaskAction
    fun apply() {
        println("APPLY $message")
    }
}
tasks.register<CustomTask>("myTask", "hello")
```