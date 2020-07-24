# Platapi Quality Plugin

This plugin enables and configures quality checks for java projects.

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
    classpath 'com.godaddy.platapi.quality:platapi-quality-plugin:0.1.2'
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

Current setup is:
- Checkstyle - ERROR
- Spotbugs - WARNING
- PMD - WARNING
- PMD:CPD - WARNING

If plugins applied manually, they would be configured too (even if auto detection didn't recognize related sources).

Also, additional javac lint options are activated to show more warnings during compilation.

All plugins are configured to produce xml and html reports. For spotbugs html report generated manually.

All plugins violations are printed into console in unified format which makes console output good enough for fixing violations.

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

