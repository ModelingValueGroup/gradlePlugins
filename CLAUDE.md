# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.modelingvalue.gradle.MvgPluginTest"

# Run a single test method
./gradlew test --tests "org.modelingvalue.gradle.UtilTest.testMethod"

# Publish (only works in CI where CI=true)
./gradlew publishPlugins

# Default task chain: mvgCorrector → test → publishPlugins → mvgTagger
./gradlew
```

## Architecture

This is a Gradle plugin (`org.modelingvalue.gradle.mvgplugin`) that automates code maintenance, version management, and CI/CD integration for ModelingValue Group projects. Published to the Gradle Plugin Portal.

### Entry Point

**`MvgPlugin.java`** implements `Plugin<Project>` and only applies to the root project. It registers five component managers, each creating a Gradle task:

| Component | Task Name | Purpose |
|-----------|-----------|---------|
| `MvgCorrector` | `mvgcorrector` | File corrections (headers, EOL, versions, bash, dependabot) |
| `MvgTagger` | `mvgtagger` | Git tagging after publish |
| `MvgBranchBasedBuilder` | `mvgbranchbasedbuilder` | Branch-aware build configuration |
| `MvgMps` | `mvgmps` | JetBrains MPS dependency resolution |
| `MvgUploader` | `mvguploader` | Plugin distribution |

### Corrector Hierarchy

The corrector system is the core of the plugin. Abstract base `Corrector` handles file overwrite safety and change tracking. `TreeCorrector` extends it with file tree traversal.

```
Corrector (abstract)
├── TreeCorrector (abstract, file tree walking)
│   ├── EolCorrector (line ending normalization)
│   └── HeaderCorrector (copyright header injection)
├── VersionCorrector (version from git tags)
├── BashCorrector (bash script fixes)
└── DependabotCorrector (dependabot config generation)
```

`MvgCorrector` orchestrates all correctors and exposes configuration via `MvgCorrectorExtension`.

### Versioning (VersionCorrector)

On CI the project version is the patch successor of the highest version-like git tag (any non-digit prefix + 3-part version: `v1.2.3`, `release1.2.3`, `1.2.3`, ...), or the `gradle.properties` version itself when that is higher than every tag - that is how a new minor/major release line is started. The `gradle.properties` file is never written back. Outside CI the version is always `dev` (local artifacts must not look like a release); `MvgTagger` refuses to tag `vdev`.

### Key Support Classes

- **`Info`** — Interface with all constants: task names, property keys, URLs, default values
- **`InfoGradle`** — Singleton for Gradle-specific runtime info (project, branch, properties)
- **`GitUtil` / `GitManager`** — JGit wrappers for git operations (tag, commit, push)
- **`Version`** — Semver parsing and comparison
- **`GradleDotProperties` / `DotProperties`** — Property file read/write
- **`dependencyHookup.kt`** — Kotlin helper providing `mpsJar(dep)` function for MPS dependency resolution

### Environment Variables

- `CI` — Enables publishing and CI-specific behavior
- `TESTING` — Enables test-specific behavior
- `ALLREP_TOKEN` — GitHub authentication for git operations
- `GITHUB_WORKFLOW` — Current GitHub Actions workflow name

## Technical Details

- **Language:** Java 21+ with one Kotlin file
- **Build:** Gradle 7.5+ with Kotlin DSL
- **Git library:** JGit 7.x
- **Testing:** JUnit Jupiter with Gradle TestKit; tests create isolated workspaces in `build/test-workspace/`. `Info.JUNIT_VERSION` must match the junit versions in `mvgplugin/build.gradle.kts` (checked by `MvgPluginTest.checkJunitVersion`), and the `TEST_MARKER_REPLACE_NOT_DONE` count in `MvgPluginTest` shifts when dependency sets change
- **Key dependencies:** Jackson (YAML), Apache HttpComponents, Gradle Enterprise plugin
