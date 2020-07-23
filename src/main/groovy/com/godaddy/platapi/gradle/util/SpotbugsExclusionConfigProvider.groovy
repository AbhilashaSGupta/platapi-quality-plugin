package com.godaddy.platapi.gradle.util

import com.github.spotbugs.snom.SpotBugsTask
import com.godaddy.platapi.gradle.ConfigLoader
import com.godaddy.platapi.gradle.PluginConfiguration
import groovy.transform.CompileStatic
import org.gradle.api.file.RegularFile
import org.slf4j.Logger

import java.util.concurrent.Callable

/**
 * Previously, exclusion config was calculated in doFirst hook of spotbugs task (just before task execution),
 * but the new spotbugs plugin use gradle properties and now value can't be set in doFirst callback!
 * Have to use property provider instead. This provider is called multiple times and in different time
 * (before all tasks execution).
 * <p>
 * One side effect of this shift caused by clean task which removes temporary copied configs, so we have now to
 * always check for already generated config existence and re-create it if removed. Overall, callback tries to
 * avoid redundant actions as much as possible.
 */
@CompileStatic
class SpotbugsExclusionConfigProvider implements Callable<RegularFile> {

  ConfigLoader loader
  PluginConfiguration extension
  SpotBugsTask task
  Logger logger

  // required to avoid duplicate calculations (because provider would be called multiple times)
  File computed

  SpotbugsExclusionConfigProvider(ConfigLoader loader, PluginConfiguration extension, SpotBugsTask task, Logger logger) {
    this.loader = loader
    this.extension = extension
    this.task = task
    this.logger = logger
  }

  @Override
  RegularFile call() throws Exception {
    // exists condition required because of possible clean task which will remove prepared
    // default file and so it must be created again (damn props!!!)
    if (computed == null || !computed.exists()) {
      File excludeFile = loader.resolveSpotbugsExclude()
      // spotbugs does not support exclude of SourceTask, so appending excluded classes to
      // xml exclude filter
      // for custom rank appending extra rank exclusion rule
      if (extension.exclude || extension.excludeSources || extension.spotbugsMaxRank < 20) {
        excludeFile = SpotbugUtils.replaceExcludeFilter(task, excludeFile, extension, logger)
      }

      computed = excludeFile
    }
    return { -> computed } as RegularFile
  }
}
