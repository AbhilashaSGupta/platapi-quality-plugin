package com.godaddy.platapi.quality.report

import groovy.transform.CompileStatic
import org.gradle.api.Task

@CompileStatic
interface Reporter<T extends Task> {

  /**
   * New line symbol.
   */
  String NL = String.format('%n')

  /**
   * Called after quality tool task to report violations.
   *
   * @param task quality task with violations
   * @param type task type (main or test)
   */
  void report(T task, String type)
}
