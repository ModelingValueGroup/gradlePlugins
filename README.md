# gradlePlugins
This repo contains the gradle plugins for some build steps in our other projects.
They might be usable for others but that is not the essence here.

# mvgCorrector
This plugin can be used to correct certain aspects during (or better before) a build.

Usage in your ```build.gradle.kts``` file:

```
plugins {
    id("org.modelingvalue.gradle.corrector")
}
```
It will scan the complete project dir.
 - all _text-like_ files will be corrected w.r.t. their line endings.
 - all _known_ file types will get a copyright header entry
 - the VERSION in ```gradle.properties``` is updated to a version that is not yet among the tags

The plugin carries a number of sensible defaults so that it can be used without any configuration.

Additional cnfig is possible though:
```
mvgCorrector {
    headerUrl = "http://blablabl"
    dry = true
    addTextFile("xyzzy")
    addNoTextFile("xyzzy")
    
    addTextFileExtension("xyzzy")
    addNoTextFileExtension("xyzzy")
    
    addHeaderFileExtension("xyzzy","###") // ext and comment prelude
    addHeaderFileExclude("xyzzy")
}
```
To download, see: https://plugins.gradle.org/plugin/org.modelingvalue.gradle.corrector
# ... other plugins might follow in the future
