package com.godaddy.platapi.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Project

/**
 * Loads configuration files either from custom configs directory (quality.configDir) or from classpath.
 * When loading from classpath all files are copied (and cached) in build/quality-configs/.
 * <p>
 * To avoid problems with clean task, default configs are copied only before actual task run. All config file
 * getters contains optional parameter copyDefaultConfig to prevent actual file copying on configuration phase.
 */
@CompileStatic
class ConfigLoader {
  private final String checkstyle = 'checkstyle/checkstyle.xml'
  private final String checkstyleSuppressions = 'checkstyle/suppressions.xml'
  private final String pmd = 'pmd/pmd.xml'
  private final String cpdXsl = 'cpd/cpdhtml.xslt'
  private final String spotbugsExclude = 'spotbugs/exclude.xml'
  private final String spotbugsXsl = 'spotbugs/html-report-style.xsl'
  private final String codenarc = 'codenarc/codenarc.xml'

  Project project
  File configDir
  File tmpConfigDir

  ConfigLoader(Project project) {
    this.project = project
  }

  File resolveCheckstyleConfig(boolean copyDefaultFile = true) {
    resolve(checkstyle, copyDefaultFile)
  }

  /**
   *  This is needed for the suppressions to work correctly by default.
   *  The path of suppressions was set to ###SUPPRESSIONS_FILEPATH###
   *  and dynamically updated to absolute path to allow suppressions to work
   *  with the checkstyle
   */
  File resolveCheckstyleSuppressionsConfig(boolean copyDefaultFile = true) {
    def checkstyleFile = resolve(checkstyle, copyDefaultFile)
    def suppressionsFile = resolve(checkstyleSuppressions, copyDefaultFile)

    checkstyleFile.text = checkstyleFile.text.replace('###SUPPRESSIONS_FILEPATH###',
            suppressionsFile.getAbsolutePath().toString())

    return suppressionsFile

  }

  File resolvePmdConfig(boolean copyDefaultFile = true) {
    resolve(pmd, copyDefaultFile)
  }

  File resolveCpdXsl(boolean copyDefaultFile = true) {
    resolve(cpdXsl, copyDefaultFile)
  }

  File resolveSpotbugsExclude(boolean copyDefaultFile = true) {
    resolve(spotbugsExclude, copyDefaultFile)
  }

  File resolveSpotbugsXsl(boolean copyDefaultFile = true) {
    resolve(spotbugsXsl, copyDefaultFile)
  }

  /**
   * Copies default configs into configured user directory.
   *
   * @param override override filed
   */
  void initUserConfigs(boolean override) {
    init()
    [checkstyle, pmd, cpdXsl, codenarc, spotbugsExclude, spotbugsXsl].each {
      copyConfig(configDir, it, override)
    }
  }

  private File resolve(String path, boolean copyDefaultFile) {
    init()
    // look custom user file first
    File target = new File(configDir, path)
    return target.exists() ?
            target
            : (copyDefaultFile ? copyConfig(tmpConfigDir, path, false) : new File(tmpConfigDir, path)) as File
  }

  private void init() {
    if (configDir == null) {
      // lazy resolution to make sure user configuration applied
      this.configDir = project.rootProject.file(project.extensions.findByType(PluginConfiguration).configDir)
      this.tmpConfigDir = project.file("$project.buildDir/quality-configs/")
    }
  }

  @SuppressWarnings('SynchronizedOnThis')
  // internal class so no monitor steal possible
  private File copyConfig(File parent, String path, boolean override) {
    File target = new File(parent, path)
    if (target.exists() && !override) {
      return target
    }
    synchronized (this) {
      if (!target.exists() || override) {
        if (target.exists() && override) {
          target.delete()
        }
        if (!target.parentFile.exists() && !target.parentFile.mkdirs()) {
          throw new IllegalStateException("Failed to create directories: $target.parentFile.absolutePath")
        }
        InputStream stream = getClass().getResourceAsStream("/com/godaddy/platapi/gradle/config/$path")
        if (stream == null) {
          throw new IllegalStateException("Default config file not found in classpath: $path")
        }
        target << stream.text
      }
      return target
    }
  }
}
