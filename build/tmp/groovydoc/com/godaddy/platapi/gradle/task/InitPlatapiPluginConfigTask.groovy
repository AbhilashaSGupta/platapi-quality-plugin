package com.godaddy.platapi.gradle.task

import com.godaddy.platapi.gradle.ConfigLoader
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Task copies default configs to user directory (platapiConfig.configDir) for customization.
 * By default, does not override existing files.
 * Registered as 'initPlatapiPluginConfigTask'.
 *
 */
@CompileStatic
class InitPlatapiPluginConfigTask extends DefaultTask {

  @Input
  boolean override

  InitPlatapiPluginConfigTask() {
    group = 'build setup'
    description = 'Copies default quality plugin configuration files for customization'
  }

  @TaskAction
  void run() {
    new ConfigLoader(project).initUserConfigs(override)
  }
}
