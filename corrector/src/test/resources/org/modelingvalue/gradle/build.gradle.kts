plugins {
    java
    id("<my-package>")
}
<myExtension> {
    addTextFileExtention("pruuperties")
    gitPush = true // TODO: do not commit or push this, only for manual testing!
}
