# Platapi Quality Plugin

![Build Status](https://api-customers.jenkins.int.godaddy.com/view/plugins/job/platapi-quality-plugin/job/master/statusbadges-build/icon) ![Grade](https://api-customers.jenkins.int.godaddy.com/view/plugins/job/platapi-quality-plugin/job/master/statusbadges-grade/icon)

Forked from [https://github.com/xvik/gradle-quality-plugin] and heavily customized.

This plugin enables and configures quality checks for java projects
* Checkstyle
* PMD
* Spotbugs
* OWASP

## Main Features
- Zero configuration by default: provided opinionated configs applied to all quality plugins
- Default configuration files may be imported to the local project and customized
- Adds extra javac lint options to see more warnings
- Complete console output for all quality plugins
- Html and xml reports for all plugins (custom xsl used for findbugs html report because it can't generate both xml and html reports)
- Grouping tasks to run registered quality plugins for exact source set (e.g. checkQualityMain)
- Dependency known vulnerability detection and upgrade simplification

## Configurations
To activate, add the following to your build gradle
```
buildscript {
  repositories {
    maven {
      credentials {
        username ci_pull_user
        password ci_pull_password
      }
      url 'https://artifactory.secureserver.net/artifactory/java-virt'
    }
  }
  dependencies {
    classpath 'com.godaddy.platapi.quality:platapi-quality-plugin:0.1.0'
  }
}

apply plugin: 'com.godaddy.platapi.quality'

```

Java project is detected by presence of java sources. Please add the following to your build gradle to identify your source sets to be analyzed:

```
sourceSets {
  main {
    java {
      srcDirs = ['src/main/java', 'build/generated/java']
    }
    resources {
      srcDirs = ['src/main/resources', 'build/generated/resources']
    }
  }
  test {
    java {
      srcDirs = ['src/test/java']
    }
    resources {
      srcDirs = ['src/test/resources']
    }
  }
}
```

In this case Checkstyle, PMD, PMD:CPD and Spotbugs plugins are activated.

To trigger the plugin:
```
./gradlew check
```
Will execute all quality plugins. Alternatively, you can use grouping task to run checks without tests.

If any violations were found then build will fail with all violations printed to console. For example like this:

```
23 PMD rule violations were found in 2 files

[Comments | CommentRequired] sample.(Sample.java:3)
  headerCommentRequirement Required
  https://pmd.github.io/pmd-5.4.0/pmd-java/rules/java/comments.html#CommentRequired

...
```
Or you can use build task (which also calls check):

```
./gradlew build
```
Current setup is:
- Checkstyle - ERROR
- Spotbugs - WARNING
- PMD - WARNING
- PMD:CPD - WARNING

If plugins applied manually, they would be configured too (even if auto detection didn't recognize related sources).

Also, additional javac lint options are activated to show more warnings during compilation.

All plugins are configured to produce xml and html reports. For spotbugs html report generated manually.

All plugins violations are printed into console in unified format which makes console output good enough for fixing violations.

## Depedency Management

Dependency checks are not wired automatically to `check` or `build` but can be run manually at any time.

To check for dependencies with known vulnerabilities, run:
```
./gradlew dependencyCheckAnalyze
```

To check for dependencies that are out of date, run:
```
./gradlew dependencyUpdates
```

To see more options for analyzing dependencies, see https://github.com/ben-manes/gradle-versions-plugin/blob/master/README.md
## Additional Configurations/Overrides
Plugin may be configured with 'platapiPluginConfiguration' closure.

For example:
```
quality {
  checkStyleStrict = false
}
```
sets checkstyle to warning level instead of error.

## Custom checkstyle configs
By default, plugin use bundled platapi gradle plugins configurations. These configs could be copied into project with 'initCustomQualityConfig' task (into quality.configDir directory). These custom configs will be used in priority with fallback to default config if config not found.

Special tasks registered for each source set: checkQualityMain, checkQualityTest etc. Tasks group registered platapi gradle plugins tasks for specific source set. This allows running platapi gradle plugins directly without tests (comparing to using 'check' task). Also, allows running platapi gradle plugins on source sets not enabled for main 'check' (example case: run quality checks for tests (time to time)). These tasks may be used even when quality tasks are disabled (quality.enabled = false).

## Development of the plugin

### To test local

```
./gradlew clean build publishToMavenLocal
```

### To release a new version

```
./gradlew clean build artifactoryPublish
```
