package com.godaddy.platapi.quality.report

import groovy.transform.CompileStatic
import org.gradle.api.Task

@CompileStatic
interface HtmlReportGenerator<T extends Task> {

  /**
   * Called after quality tool task to generate html report.
   *
   * @param task quality task to generate report for
   * @param type task type (main or test)
   */
  void generateHtmlReport(T task, String type)
}
