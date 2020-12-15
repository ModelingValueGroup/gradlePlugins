buildscript {
    dependencies {
        classpath(files("corrector/build/libs/corrector-0.1.jar"))
    }
}

apply(plugin = "org.modelingvalue.gradle.corrector.MvgCorrectorPlugin")

mvgCorrector {
    dry = true
    textExtensions.add("pruuperties")
}
