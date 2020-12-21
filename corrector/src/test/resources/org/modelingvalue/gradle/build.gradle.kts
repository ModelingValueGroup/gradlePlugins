project.version = "0.0.1"
plugins {
    java
    id("<my-package>")
}
<myExtension> {
    addTextFileExtension("pruuperties")
    gitPush = true
}
