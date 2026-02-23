# gradlePlugins
This repo contains the gradle plugins for some build steps in our other projects.
They might be usable for others but that is not the essence here.

# mvgplugin
This plugin can be used to correct certain aspects during (or better before) a build.

Usage in your ```build.gradle.kts``` file:

```
plugins {
    id("org.modelingvalue.gradle.mvgplugin")
}
```
It will scan the complete project dir.
 - all _text-like_ files will be corrected w.r.t. their line endings.
 - all _known_ file types will get a copyright header entry
 - the ```version``` in ```gradle.properties``` is updated to a version that is not yet among the tags
 - all projects will have ```group``` and ```version``` set from what is in the properties file

The plugin carries a number of sensible defaults so that it can be used without any configuration.

Additional cnfig is possible though:
```
mvgplugin {
    headerUrl = "http://blablabl"
    addTextFile("xyzzy")
    addNoTextFile("xyzzy")
    
    addTextFileExtension("xyzzy")
    addNoTextFileExtension("xyzzy")
    
    addHeaderFileExtension("xyzzy","###") // ext and comment prelude
    addHeaderFileExclude("xyzzy")
}
```
To download, see: https://plugins.gradle.org/plugin/org.modelingvalue.gradle.mvgplugin

## Alternative: consume from GitHub Pages

The plugin is also published as a Maven artifact to GitHub Pages. To use it from there, add the following to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://modelingvaluegroup.github.io/gradlePlugins/") }
        gradlePluginPortal()
    }
}
```

Then apply the plugin as usual in your `build.gradle.kts`:
```kotlin
plugins {
    id("org.modelingvalue.gradle.mvgplugin") version "2.0.0"
}
```

# ... other plugins might follow in the future
