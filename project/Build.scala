import sbt._
import Keys._
import play.Project._
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

object ApplicationBuild extends Build with ProjectSettings {
  override def settings = super.settings ++ Seq(
    EclipseKeys.skipParents in ThisBuild := false,
    scalaVersion := ourScalaVersion,
    resolvers ++= ourResolvers)

  val allJavaOpts = Seq("-Duser.timezone=UTC")

  val workerJavaOpts = Seq("-Dlogback.configurationFile=workerdevlog.xml") ++: {
    if (System.getProperty("datasource.default.url") == null) Seq("-Ddatasource.default.url=" + appDatabaseUrl)
    else Nil
  }

  val ourTestWithNoDbOptions = Seq(
    Tests.Argument("xonly")/*,
    Tests.Setup { loader =>
      // Load Logger so configurations happen in the right order
      loader.loadClass("org.slf4j.LoggerFactory")
        .getMethod("getLogger", loader.loadClass("java.lang.String"))
        .invoke(null, "ROOT")
    }
    */
  )

  val ourTestOptions = ourTestWithNoDbOptions ++ Seq(
    Tests.Setup { () =>
      System.setProperty("datasource.default.url", testDatabaseUrl)
      System.setProperty("logback.configurationFile", "logback-test.xml")
    }/*,
    Tests.Setup(loader => ClearTestDatabase(loader))*/)

  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath =>
    println(classpath.map(_.data).mkString(":"))
  }

  val messageBroker = Project("message-broker", file("message-broker"),
                              settings = Defaults.defaultSettings ++ OverviewCommands.defaultSettings)
    .settings(packageArchetype.java_application: _*)
    .settings(
      libraryDependencies ++= messageBrokerDependencies,
      printClasspath
    )

  val searchIndex = Project("search-index", file("search-index"),
                            settings = Defaults.defaultSettings ++ OverviewCommands.defaultSettings)
    .settings(packageArchetype.java_application: _*)
    .settings(
      libraryDependencies ++= searchIndexDependencies,
      Keys.fork := true,
      javaOptions in run <++= (baseDirectory) map { (d) =>
        Seq(
          "-Des.path.home=" + d,
          "-Xms1g", "-Xmx1g", "-Xss256k",
          "-XX:+UseParNewGC",  "-XX:+UseConcMarkSweepGC", "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly",
          "-Djava.awt.headless=true",
          "-Delasticsearch",
          "-Des.foreground=yes"
        )
      },
      printClasspath
    )

  val runner = Project(
      "runner",
      file("runner"),
      settings = Defaults.defaultSettings)
    .settings(packageArchetype.java_application: _*)
    .settings(libraryDependencies ++= runnerDependencies)
    .settings(scalacOptions ++= ourScalacOptions)
    .settings(parallelExecution in Test := false) // Scallop has icky races. There may be occasional errors with this option, but far fewer than without

  val dbEvolutionApplier = (Project(
      "db-evolution-applier",
      file("db-evolution-applier"),
      settings = Defaults.defaultSettings)
    .settings(packageArchetype.java_application: _*)
    .settings(
      libraryDependencies ++= dbEvolutionApplierDependencies,
      scalacOptions ++= ourScalacOptions,
      mappings in (Compile, packageBin) <++= baseDirectory map { base =>
        val evolutions = ((base / ".." / "conf" / "evolutions") ** "*").get
        evolutions pair relativeTo(base / ".." / "conf")
      }
    )
  )

  // Create a subProject with our common settings
  object OverviewProject extends OverviewCommands with OverviewKeys {
    def apply(name: String, dependencies: Seq[ModuleID],
              useSharedConfig: Boolean = true,
              theTestOptions: Seq[TestOption] = ourTestOptions) = {
      Project(name, file(name), settings =
        Defaults.defaultSettings ++
          defaultSettings ++
          Seq(printClasspath) ++
          addUnmanagedResourceDirectory(useSharedConfig) ++
          Seq(
            libraryDependencies ++= dependencies,
            testOptions in Test ++= theTestOptions,
            scalacOptions ++= ourScalacOptions,
            logBuffered := false,
            parallelExecution in Test := false,
            sources in doc in Compile := List(),
            printClasspath))
    }

    // don't clean the database if it isn't being used in tests
    def withNoDbTests(name: String, dependencies: Seq[ModuleID], useSharedConfig: Boolean = true,
                      theTestOptions: Seq[TestOption] = ourTestWithNoDbOptions) = apply(name, dependencies, useSharedConfig, theTestOptions)

    private def addUnmanagedResourceDirectory(useSharedConfig: Boolean) =
      if (useSharedConfig) Seq(unmanagedResourceDirectories in Compile <+= baseDirectory { _ / "../worker-conf" })
      else Seq()
  }

  // Project definitions
  val common = OverviewProject("common", commonProjectDependencies, useSharedConfig = false)

  /*
   * Ideally, common would depend on commonTest, which would mock out the
   * database.
   *
   * Reality is the other way around. The test suite relies on the database,
   * which common provides.
   */
  val commonTest = OverviewProject("common-test", commonTestProjectDependencies, useSharedConfig = false)
    .dependsOn(common)

  val documentSetWorker = OverviewProject.withNoDbTests("documentset-worker", documentSetWorkerProjectDependencies)
    .settings(packageArchetype.java_application: _*)
    .settings(
      Keys.fork := true,
      javaOptions in run ++= allJavaOpts ++ workerJavaOpts,
      javaOptions in Test += "-Dlogback.configurationFile=logback-test.xml")
    .dependsOn(common)
    .dependsOn(commonTest % "test")

  val worker = OverviewProject("worker", workerProjectDependencies)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      Keys.fork := true,
      javaOptions in run ++=  allJavaOpts ++ workerJavaOpts,
      javaOptions in Test += "-Dlogback.configurationFile=logback-test.xml"
    )
    .dependsOn(common)
    .dependsOn(commonTest % "test")

  val main = (
    play.Project(appName, appVersion, serverProjectDependencies)
      .settings(resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/")
      .settings(
        scalacOptions ++= ourScalacOptions,
        templatesImport += "views.Magic._",
        routesImport += "extensions.Binders._",
        requireJs ++= Seq(
          "bundle/admin/ImportJob/index.js",
          "bundle/admin/User/index.js",
          "bundle/CsvUpload/new.js",
          "bundle/DocumentCloudImportJob/new.js",
          "bundle/DocumentCloudProject/index.js",
          "bundle/DocumentSet/index.js",
          "bundle/DocumentSet/show.js",
          "bundle/Document/show.js",
          "bundle/PdfUpload/new.js",
          "bundle/PublicDocumentSet/index.js",
          "bundle/SharedDocumentSet/index.js",
          "bundle/Welcome/show.js"
        ),
        requireJsShim += "main.js",
        aggregate in Compile := true,
        parallelExecution in IntegrationTest := false,
        javaOptions in run ++= allJavaOpts,
        javaOptions in Test ++= allJavaOpts ++ Seq(
          "-Dconfig.file=conf/application-test.conf",
          "-Dlogger.resource=logback-test.xml",
          "-Ddb.default.url=" + testDatabaseUrl,
          "-XX:MaxPermSize=256m"),
        Keys.fork in Test := true,
        aggregate in Test := false,
        testOptions in Test ++= ourTestOptions,
        logBuffered := false,
        sources in doc in Compile := List(),
        printClasspath,
        aggregate in printClasspathTask := false,
        // Use native RequireJS (at Play's version -- see package.json) if it exists
        // Enable this huge speedup by running "npm install" in the base directory
        requireNativePath <<= baseDirectory(_ / "node_modules" / "requirejs" / "bin" / "r.js")
          .apply( (tryPath) =>
            if (FileInfo.exists(tryPath).exists) {
              Some(tryPath.toString)
            } else {
              None
            }
          )
      )
      .settings(
        if (scala.util.Properties.envOrElse("COMPILE_LESS", "true") == "false") {
          play.Keys.lessEntryPoints := Nil
        } else {
          play.Keys.lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" / "main.less")
        }
      )
      .dependsOn(common)
      .dependsOn(commonTest % "test")
    )

  val all = Project("all", file("all"))
    .aggregate(main, worker, documentSetWorker, common)
    .settings(
      aggregate in Test := false,
      test in Test <<= (test in Test in main)
        dependsOn (test in Test in worker)
        dependsOn (test in Test in documentSetWorker)
        dependsOn (test in Test in common)
    )
}
