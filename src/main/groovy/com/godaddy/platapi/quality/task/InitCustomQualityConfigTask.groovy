package com.godaddy.platapi.quality.task

import com.godaddy.platapi.quality.ConfigLoader
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Task copies default configs to user directory (platapiConfig.configDir) for customization.
 * By default, does not override existing files.
 * Registered as 'initCustomQualityConfig'.
 *
 */
@CompileStatic
class InitCustomQualityConfigTask extends DefaultTask {

  @Input
  boolean override

  InitCustomQualityConfigTask() {
    group = 'build setup'
    description = 'Copies default platapi gradle plugin configuration files for customization'
  }

  @TaskAction
  void run() {
    new ConfigLoader(project).initUserConfigs(override)
  }
}
