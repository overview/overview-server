import sbt._
import sbt.Keys._
import play.Play.autoImport._
import com.typesafe.sbt.rjs.SbtRjs.autoImport._
import com.typesafe.sbt.digest.SbtDigest.autoImport._
import com.typesafe.sbt.gzip.SbtGzip.autoImport._
import com.typesafe.sbt.coffeescript.SbtCoffeeScript.autoImport._
import com.typesafe.sbt.jse.SbtJsEngine.autoImport._
import com.typesafe.sbt.less.SbtLess.autoImport._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.SbtWeb
import PlayKeys._
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import play.twirl.sbt.Import._

object ApplicationBuild extends Build with ProjectSettings {
  override def settings = super.settings ++ Seq(
    EclipseKeys.skipParents in ThisBuild := false,
    scalaVersion := ourScalaVersion,
    resolvers ++= ourResolvers
  )

  val allJavaOpts = Seq("-Duser.timezone=UTC")

  val workerJavaOpts = Seq("-Dlogback.configurationFile=workerdevlog.xml") ++: {
    if (System.getProperty("datasource.default.url") == null) Seq("-Ddatasource.default.url=" + appDatabaseUrl)
    else Nil
  }

  val ourTestWithNoDbOptions = Seq(
    Tests.Argument(TestFrameworks.Specs2, "xonly")/*,
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
    }
  )

  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath =>
    println(classpath.map(_.data).mkString(":"))
  }

  val messageBroker = Project("message-broker", file("message-broker"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      libraryDependencies ++= messageBrokerDependencies,
      printClasspath
    )

  val searchIndex = Project("search-index", file("search-index"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
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

  val runner = Project("runner", file("runner"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(libraryDependencies ++= runnerDependencies)
    .settings(scalacOptions ++= ourScalacOptions)
    .settings(parallelExecution in Test := false) // Scallop has icky races. There may be occasional errors with this option, but far fewer than without

  val dbEvolutionApplier = Project("db-evolution-applier", file("db-evolution-applier"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      libraryDependencies ++= dbEvolutionApplierDependencies,
      scalacOptions ++= ourScalacOptions,
      mappings in (Compile, packageBin) <++= baseDirectory map { base =>
        val evolutions = ((base / ".." / "conf" / "evolutions") ** "*").get
        evolutions pair relativeTo(base / ".." / "conf")
      }
    )

  // Create a subProject with our common settings
  object OverviewProject {
    def apply(name: String, dependencies: Seq[ModuleID],
              useSharedConfig: Boolean = true,
              theTestOptions: Seq[TestOption] = ourTestOptions) = {
      Project(name, file(name))
        .settings(Defaults.coreDefaultSettings: _*)
        .settings(addUnmanagedResourceDirectory(useSharedConfig): _*)
        .settings(
          libraryDependencies ++= dependencies,
          testOptions in Test ++= theTestOptions,
          scalacOptions ++= ourScalacOptions,
          logBuffered := false,
          parallelExecution in Test := false,
          sources in doc in Compile := List(),
          printClasspath
        )
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
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
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
    Project(appName, file("."))
      .enablePlugins(play.PlayScala)
      .enablePlugins(SbtWeb)
      .settings(resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/")
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
      .settings(
        version := appVersion,
        libraryDependencies ++= serverProjectDependencies,
        scalacOptions ++= ourScalacOptions,
        TwirlKeys.templateImports += "views.Magic._",
        routesImport += "extensions.Binders._",
        RjsKeys.modules := Seq(
          WebJs.JS.Object("name" -> "bundle/admin/ImportJob/index"),
          WebJs.JS.Object("name" -> "bundle/admin/User/index"),
          WebJs.JS.Object("name" -> "bundle/ApiToken/index"),
          WebJs.JS.Object("name" -> "bundle/CsvUpload/new"),
          WebJs.JS.Object("name" -> "bundle/DocumentCloudImportJob/new"),
          WebJs.JS.Object("name" -> "bundle/DocumentCloudProject/index"),
          WebJs.JS.Object("name" -> "bundle/DocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/DocumentSet/show"),
          WebJs.JS.Object("name" -> "bundle/Document/show"),
          WebJs.JS.Object("name" -> "bundle/FileImport/new"),
          WebJs.JS.Object("name" -> "bundle/PublicDocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/SharedDocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/Welcome/show")
        ),
        aggregate in Compile := true,
        parallelExecution in IntegrationTest := false,
        javaOptions in run ++= allJavaOpts,
        javaOptions in Test ++= allJavaOpts ++ Seq(
          "-Dconfig.file=conf/application-test.conf",
          "-Dlogger.resource=logback-test.xml",
          "-Ddb.default.url=" + testDatabaseUrl,
          "-XX:MaxPermSize=256m"
        ),
        Keys.fork in Test := true,
        aggregate in Test := false,
        testOptions in Test ++= ourTestOptions,
        logBuffered := false,
        sources in doc in Compile := List(),
        printClasspath,
        aggregate in printClasspathTask := false,
        includeFilter in (Assets, LessKeys.less) := "main.less",
        includeFilter in (TestAssets, CoffeeScriptKeys.coffeescript) := "",
        pipelineStages := Seq(rjs, digest, gzip)
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
