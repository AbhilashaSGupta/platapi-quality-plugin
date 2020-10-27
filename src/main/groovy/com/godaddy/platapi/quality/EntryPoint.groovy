package com.godaddy.platapi.quality

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.spotbugs.snom.SpotBugsTask
import com.godaddy.platapi.quality.report.CheckstyleReporter
import com.godaddy.platapi.quality.report.CpdReporter
import com.godaddy.platapi.quality.report.HtmlReportGenerator
import com.godaddy.platapi.quality.report.PmdReporter
import com.godaddy.platapi.quality.report.Reporter
import com.godaddy.platapi.quality.report.SpotbugsReporter
import com.godaddy.platapi.quality.spotbugs.CustomSpotBugsPlugin
import com.godaddy.platapi.quality.task.InitCustomQualityConfigTask
import com.godaddy.platapi.quality.util.CpdUtils
import com.godaddy.platapi.quality.util.DurationFormatter
import com.godaddy.platapi.quality.util.SpotbugUtils
import com.godaddy.platapi.quality.util.SpotbugsExclusionConfigProvider
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension
import org.owasp.dependencycheck.gradle.tasks.Aggregate
import org.owasp.dependencycheck.gradle.tasks.Analyze
import org.owasp.dependencycheck.gradle.tasks.Purge
import org.owasp.dependencycheck.gradle.tasks.Update
import com.github.benmanes.gradle.versions.VersionsPlugin;

/**
 * This plugin enables and configures static code analysis gradle plugins for java projects.
 * <p>
 * Java project is detected by presence of java sources. In this case Checkstyle, PMD, PMD:CPD and Spotbugs plugins are
 * activated. If plugins applied manually, they would be configured too (even if auto detection didn't
 * recognize related sources). Also, additional javac lint options are activated to show more warnings
 * during compilation.
 * <p>
 * All plugins are configured to produce xml and html reports. For spotbugs html report
 * generated manually. All plugins violations are printed into console in unified format which makes console
 * output good enough for fixing violations.
 * <p>
 * Plugin may be configured with 'quality' closure. See {@link PluginConfiguration} for configuration options.
 * <p>
 * By default, plugin use bundled platapi gradle plugins configurations. These configs could be copied into project
 * with 'initCustomQualityConfig' task (into quality.configDir directory). These custom configs will be used in
 * priority with fallback to default config if config not found.
 * <p>
 * Special tasks registered for each source set: checkQualityMain, checkQualityTest etc.
 * Tasks group registered platapi gradle plugins tasks for specific source set. This allows running platapi gradle plugins
 * directly without tests (comparing to using 'check' task). Also, allows running platapi gradle plugins on source sets
 * not enabled for main 'check' (example case: run quality checks for tests (time to time)). These tasks may be
 * used even when quality tasks are disabled ({@code quality.enabled = false}).
 *
 */
@CompileStatic
class EntryPoint implements Plugin<Project> {

  private static final String PLATAPI_TASK = 'checkQuality'
  public static final String ANALYZE_TASK = 'dependencyCheckAnalyze'
  public static final String AGGREGATE_TASK = 'dependencyCheckAggregate'
  public static final String UPDATE_TASK = 'dependencyCheckUpdate'
  public static final String PURGE_TASK = 'dependencyCheckPurge'
  private static final String CHECK_EXTENSION_NAME = "dependencyCheck"

  @Override
  void apply(Project project) {
    // activated only when java plugin is enabled
    project.plugins.withType(JavaPlugin) {
      PluginConfiguration platapiConfig = project.extensions.create('quality', PluginConfiguration, project)
      addInitConfigTask(project)
      project.afterEvaluate {
        configureGroupingTasks(project)
        Context context = createContext(platapiConfig)
        ConfigLoader configLoader = new ConfigLoader(project)
        configureJavac(project, platapiConfig)
        applyCheckstyle(project, platapiConfig, configLoader, context.registerJavaPlugins)
        applyPMD(project, platapiConfig, configLoader, context.registerJavaPlugins)
        applySpotbugs(project, platapiConfig, configLoader, context.registerJavaPlugins)
        configureCpdPlugin(project, platapiConfig, configLoader, context.registerJavaPlugins)
        configureDependencyScanTasks(project)
        configureDependencyUpdateTask(project)
      }
    }

  }

  private static void addInitConfigTask(Project project) {
    project.tasks.register('initCustomQualityConfig', InitCustomQualityConfigTask)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void configureGroupingTasks(Project project) {
    // create checkQualityMain, checkQualityTest (quality tasks for all source sets)
    // using all source sets and not just declared in platapiConfig to be able to run platapi gradle plugins
    // on source sets which are not included in check task run (e.g. run quality on tests time to time)
    project.sourceSets.each {SourceSet set ->
      project.tasks.register(set.getTaskName(PLATAPI_TASK, null)) {
        it.with {
          group = 'verification'
          description = "Run platapi plugins for $set.name source set"
        }
      }
    }
  }

  private static void configureJavac(Project project, PluginConfiguration platapiConfig) {
    if (!platapiConfig.lintOptions) {
      return
    }
    project.tasks.withType(JavaCompile).configureEach {JavaCompile t ->
      t.options.compilerArgumentProviders
              .add({
                platapiConfig.lintOptions.collect {
                  "-Xlint:$it" as String
                }
              } as CommandLineArgumentProvider)
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void applyCheckstyle(Project project, PluginConfiguration platapiConfig, ConfigLoader configLoader,
                               boolean register) {
    configurePlugin(project,
            platapiConfig.checkstyle,
            register,
            CheckstylePlugin) {
      project.configure(project) {
        checkstyle {
          showViolations = true
          toolVersion = platapiConfig.checkstyleVersion
          ignoreFailures = !platapiConfig.checkstyleStrict
          configFile = configLoader.resolveCheckstyleConfig(false)
          sourceSets = platapiConfig.sourceSets
        }
        tasks.withType(Checkstyle).configureEach {
          doFirst {
            configLoader.resolveCheckstyleConfig()
            if (platapiConfig.useSuppressions)
              configLoader.resolveCheckstyleSuppressionsConfig()
            applyExcludes(it, platapiConfig)
          }
          reports {
            xml.enabled = true
            html.enabled = platapiConfig.htmlReports
          }
        }
      }
      configurePluginTasks(project, platapiConfig, Checkstyle, 'checkstyle', new CheckstyleReporter(configLoader))
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void applyPMD(Project project, PluginConfiguration platapiConfig, ConfigLoader configLoader,
                        boolean register) {
    configurePlugin(project,
            platapiConfig.pmd,
            register,
            PmdPlugin) {
      project.configure(project) {
        pmd {
          toolVersion = platapiConfig.pmdVersion
          ignoreFailures = !platapiConfig.pmdStrict
          ruleSets = []
          ruleSetFiles = files(configLoader.resolvePmdConfig(false).absolutePath)
          sourceSets = platapiConfig.sourceSets
        }
        if (platapiConfig.pmdIncremental) {
          if (platapiConfig.metaClass.properties.any {
            it.name == 'incrementalAnalysis'
          }) {
            pmd.incrementalAnalysis = true
          } else {
            project.logger.warn('WARNING: PMD incremental analysis option ignored, because it\'s '
                    + 'supported only from gradle 5.6')
          }
        }
        tasks.withType(Pmd).configureEach {
          doFirst {
            configLoader.resolvePmdConfig()
            applyExcludes(it, platapiConfig)
          }
          reports {
            xml.enabled = true
            html.enabled = platapiConfig.htmlReports
          }
        }
      }
      configurePluginTasks(project, platapiConfig, Pmd, 'pmd', new PmdReporter())
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @SuppressWarnings('MethodSize')
  private void applySpotbugs(Project project, PluginConfiguration platapiConfig, ConfigLoader configLoader,
                             boolean register) {
    SpotbugUtils.validateRankSetting(platapiConfig.spotbugsMaxRank)

    configurePlugin(project,
            platapiConfig.spotbugs,
            register,
            CustomSpotBugsPlugin) {
      project.configure(project) {
        spotbugs {
          toolVersion = platapiConfig.spotbugsVersion
          ignoreFailures = !platapiConfig.spotBugStrict
          effort = platapiConfig.spotbugsEffort
          reportLevel = platapiConfig.spotbugsLevel
          // note: default excludeFilter is not set not set in platapiConfig, instead it is directly
          // set to all tasks. If you try to set it in platapiConfig, value will be ignored

          // in gradle 5 default 1g was changed and so spotbugs fails on large projects (recover behaviour),
          // but not if value set manually
          maxHeapSize.convention(platapiConfig.spotbugsMaxHeapSize)
        }

        // plugins shortcut
        platapiConfig.spotbugsPlugins?.each {
          project.configurations.getByName('spotbugsPlugins').dependencies.add(
                  project.dependencies.create(it)
          )
        }

        tasks.withType(SpotBugsTask).configureEach {task ->
          // have to use this way instead of doFirst hook, because nothing else will work (damn props!)
          excludeFilter.set(project.provider(new SpotbugsExclusionConfigProvider(configLoader,
                  platapiConfig, task, logger)))
          reports {
            xml {
              enabled true
            }
          }
        }
      }
      configurePluginTasks(project, platapiConfig, SpotBugsTask, 'spotbugs', new SpotbugsReporter(configLoader))
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @SuppressWarnings('MethodSize')
  private void configureCpdPlugin(Project project, PluginConfiguration platapiConfig, ConfigLoader configLoader,
                                  boolean register) {
    if (!platapiConfig.cpd) {
      return
    }

    CpdUtils.findAndConfigurePlugin(project) {Project prj, Plugin plugin ->
      // STAGE1 for multi-module project this part applies by all modules with platapi gradle plugin enabled
      prj.configure(prj) {
        cpd {
          toolVersion = platapiConfig.pmdVersion
          ignoreFailures = !platapiConfig.pmdStrict
        }
        // only default task is affected
        tasks.named('cpdCheck').configure {
          doFirst {
            if (platapiConfig.cpdUnifySources) {
              applyExcludes(it, platapiConfig)
            }
          }
        }
      }
      // cpdCheck is always declared by cpd plugin
      TaskProvider<SourceTask> cpdCheck = CpdUtils.findCpdTask(prj)
      if (platapiConfig.cpdUnifySources) {
        // exclude sources, not declared for quality checks in platapi gradle plugin declaration project
        CpdUtils.unifyCpdSources(project, cpdCheck, platapiConfig.sourceSets)
      }

      // STAGE2 for multi-module project everything below must be applied just once
      if (CpdUtils.isCpdAlreadyConfigured(prj)) {
        return
      }

      Class<Task> cpdTasksType = plugin.class.classLoader.loadClass('de.aaschmid.gradle.plugins.cpd.Cpd') as Class<Task>
      // reports applied for all registered cpd tasks
      prj.tasks.withType(cpdTasksType).configureEach {task ->
        reports {
          xml.enabled = true
        }
        doFirst {
          configLoader.resolveCpdXsl()
        }
        // console reporting for each cpd task
        applyReporter(prj, task.name, new CpdReporter(configLoader),
                platapiConfig.consoleReporting, platapiConfig.htmlReports)
      }
      // cpd plugin recommendation: module check must also run cpd (check module changes for duplicates)
      // grouping tasks (checkQualityMain) are not affected because cpd applied to all source sets
      // For single module projects simply make sure check will trigger cpd
      project.tasks.named('check').configure {
        it.dependsOn prj.tasks.withType(cpdTasksType)
      }

      // cpd disabled together with all platapi gradle plugins
      // yes, it's not completely normal that module could disable root project task, but it would be much
      // simpler to use like that (because platapi gradle plugin assumed to be applied in subprojects section)
      applyEnabledState(prj, platapiConfig, cpdTasksType)
    }
  }

  private static void applyReporter(Project project, String type, Reporter reporter,
                                    boolean consoleReport, boolean htmlReport) {
    boolean generatesHtmlReport = htmlReport && HtmlReportGenerator.isAssignableFrom(reporter.class)
    if (!consoleReport && !generatesHtmlReport) {
      // nothing to do at all
      return
    }
    // in multi-project reporter registered for each project, but all gets called on task execution in any module
    project.gradle.taskGraph.afterTask {Task task, TaskState state ->
      if (task.name.startsWith(type) && project == task.project) {
        // special case for cpd where single task used for all source sets
        String taskType = task.name == type ? type : task.name[type.length()..-1].toLowerCase()
        if (generatesHtmlReport) {
          (reporter as HtmlReportGenerator).generateHtmlReport(task, taskType)
        }
        if (consoleReport) {
          long start = System.currentTimeMillis()
          reporter.report(task, taskType)
          String duration = DurationFormatter.format(System.currentTimeMillis() - start)
          task.project.logger.info("[plugin:platapi] $type reporting executed in $duration")
        }
      }
    }
  }

  /**
   * Detects available source folders in configured source sets to understand
   * what sources are available. Based on that knowledge
   * appropriate plugins could be registered.
   *
   * @param project project instance
   * @param platapiConfig platapiConfig instance
   * @return context instance
   */
  @CompileStatic(TypeCheckingMode.SKIP)
  private static Context createContext(PluginConfiguration platapiConfig) {
    Context context = new Context()
    if (platapiConfig.autoRegistration) {
      context.registerJavaPlugins = (platapiConfig.sourceSets.find {
        it.java.srcDirs.find {
          it.exists()
        }
      }) != null
    }
    context
  }

  /**
   * Plugins may be registered manually and in this case plugin will also will be configured, but only
   * when plugin support not disabled by quality configuration. If plugin not registered and
   * sources auto detection allow registration - it will be registered and then configured.
   *
   * @param project project instance
   * @param enabled true if platapi gradle plugin support enabled for plugin
   * @param register true to automatically register plugin
   * @param plugin plugin class
   * @param config plugin configuration closure
   */
  private static void configurePlugin(Project project, boolean enabled, boolean register, Class plugin, Closure config) {
    if (!enabled) {
      // do not configure even if manually registered
      return
    } else if (register) {
      // register plugin automatically
      project.plugins.apply(plugin)
    }
    // configure plugin if registered (manually or automatic)
    project.plugins.withType(plugin) {
      config.call()
    }
  }

  /**
   * Applies reporter, enabled state control and checkQuality* grouping tasks.
   *
   * @param project project instance
   * @param platapiConfig platapiConfig instance
   * @param taskType task class
   * @param task task base name
   * @param reporter plugin specific reporter instance
   */
  private static void configurePluginTasks(Project project, PluginConfiguration platapiConfig,
                                           Class taskType, String task, Reporter reporter) {
    applyReporter(project, task, reporter, platapiConfig.consoleReporting, platapiConfig.htmlReports)
    applyEnabledState(project, platapiConfig, taskType)
    groupQualityTasks(project, task)
  }

  /**
   * If quality tasks are disabled in configuration ({@code quality.enabled = false})
   * then disabling tasks. Anyway, task must not be disabled if called directly
   * or through grouping quality task (e.g. checkQualityMain).
   * NOTE: if, for example, checkQualityMain is called after some other task
   * (e.g. someTask.dependsOn checkQualityMain) then quality tasks will be disabled!
   * Motivation is: plugins are disabled for a reason and could be enabled only when called
   * directly (because obviously user wants quality task(s) to run).
   *
   * @param project project instance
   * @param platapiConfig platapiConfig instance
   * @param task platapi gradle plugin task class
   */
  private static void applyEnabledState(Project project, PluginConfiguration platapiConfig, Class task) {
    if (!platapiConfig.enabled) {
      project.gradle.taskGraph.whenReady {TaskExecutionGraph graph ->
        project.tasks.withType(task).configureEach {Task t ->
          // last task on stack obtained only on actual task usage
          List<Task> tasks = graph.allTasks
          Task called = tasks != null && !tasks.empty ? tasks.last() : null
          // enable task only if it's called directly or through grouping task
          t.enabled = called != null && (called == t || called.name.startsWith(PLATAPI_TASK))
        }
      }
    }
  }

  /**
   * Applies exclude path patterns to quality tasks.
   * Note: this does not apply to animalsniffer. For spotbugs this appliance is useless, see custom support above.
   *
   * @param task quality task
   * @param platapiConfig platapiConfig instance
   */
  private static void applyExcludes(SourceTask task, PluginConfiguration platapiConfig) {
    if (platapiConfig.excludeSources) {
      // directly excluded sources
      task.source = task.source - platapiConfig.excludeSources
    }
    if (platapiConfig.exclude) {
      // exclude by patterns (relative to source roots)
      task.exclude platapiConfig.exclude
    }
  }

  /**
   * platapi gradle plugins register tasks for each source set. Declared affected source sets
   * only affects which tasks will 'check' depend on.
   * Grouping tasks allow to call quality tasks, not included to 'check'.
   *
   * @param project project instance
   * @param task task base name
   */
  @CompileStatic(TypeCheckingMode.SKIP)
  private static void groupQualityTasks(Project project, String task) {
    // each plugin generate separate tasks for each source set
    // assign plugin tasks to source set grouping quality task
    project.sourceSets.each {
      TaskProvider pluginTask = project.tasks.named(it.getTaskName(task, null))
      if (pluginTask) {
        project.tasks.named(it.getTaskName(PLATAPI_TASK, null)).configure {
          it.dependsOn pluginTask
        }
      }
    }
  }

  static void configureDependencyScanTasks(Project project) {
    project.extensions.create(CHECK_EXTENSION_NAME, DependencyCheckExtension, project)
    project.tasks.register(PURGE_TASK, Purge)
    project.tasks.register(UPDATE_TASK, Update)
    project.tasks.register(ANALYZE_TASK, Analyze)
    project.tasks.register(AGGREGATE_TASK, Aggregate)
  }

  static void configureDependencyUpdateTask(Project project) {
    project.getPluginManager().apply(VersionsPlugin.class);
  }

  /**
   * Internal configuration context.
   */
  static class Context {
    boolean registerJavaPlugins
  }
}
