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

  val devJavaOpts = Seq(
    "-Ddb.default.dataSource.databaseName=overview-dev",
    "-Ddb.default.dataSource.portNumber=9010"
  )

  val testJavaOpts = Seq(
    "-Ddb.default.dataSource.databaseName=overview-test",
    "-Ddb.default.dataSource.portNumber=9010"
  )

  val workerJavaOpts = Seq("-Dlogback.configurationFile=workerdevlog.xml")

  val ourTestOptions = Seq(
    Tests.Argument(TestFrameworks.Specs2, "xonly"),
    Tests.Argument(TestFrameworks.Specs2, "showtimes"),
    Tests.Argument("junitxml", "console")
  )

  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath =>
    println(classpath.map(_.data).mkString(":"))
  }

  val messageBroker = Project("message-broker", file("message-broker"))
    .settings(scalaVersion := "2.10.5")
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
      javaOptions ++= allJavaOpts ++ devJavaOpts,
      scalacOptions ++= ourScalacOptions,
      mappings in (Compile, packageBin) <++= baseDirectory map { base =>
        val evolutions = ((base / ".." / "conf" / "evolutions") ** "*").get
        evolutions pair relativeTo(base / ".." / "conf")
      }
    )

  // Create a subProject with our common settings
  object OverviewProject {
    def apply(name: String, dependencies: Seq[ModuleID]) = {
      Project(name, file(name))
        .settings(Defaults.coreDefaultSettings: _*)
        .settings(
          unmanagedResourceDirectories in Compile <+= baseDirectory { _ / "../worker-conf" },
          libraryDependencies ++= dependencies,
          javaOptions in run ++= allJavaOpts ++ devJavaOpts,
          javaOptions in Test ++= allJavaOpts ++ testJavaOpts,

          fork := true, // to set javaOptions
          testOptions in Test ++= ourTestOptions,
          scalacOptions ++= ourScalacOptions,
          logBuffered := false,
          parallelExecution in Test := false,
          sources in doc in Compile := List(),
          printClasspath
        )
    }
  }

  // Project definitions
  val common = OverviewProject("common", commonProjectDependencies)

  val upgrade20141210MovePages = Project("upgrade-2014-12-10-move-pages", file("upgrade/2014-12-10-move-pages"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      scalacOptions ++= ourScalacOptions,
      resourceDirectory in Compile := (baseDirectory.value / ".." / ".." / "conf"),
      includeFilter in (Compile, resourceDirectory) := "application.conf"
    )
    .dependsOn(common)

  val upgrade20150119MoveFiles = Project("upgrade-2015-01-19-move-files", file("upgrade/2015-01-19-move-files"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      scalacOptions ++= ourScalacOptions,
      resourceDirectory in Compile := (baseDirectory.value / ".." / ".." / "conf"),
      includeFilter in (Compile, resourceDirectory) := "application.conf"
    )
    .dependsOn(common)

  val reindexDocuments = Project("reindex-documents", file("upgrade/reindex-documents"))
    .settings(Defaults.coreDefaultSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(scalacOptions ++= ourScalacOptions)
    .settings(libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0")
    .dependsOn(common)

  /*
   * Ideally, common would depend on commonTest, which would mock out the
   * database.
   *
   * Reality is the other way around. The test suite relies on the database,
   * which common provides.
   */
  val commonTest = OverviewProject("common-test", commonTestProjectDependencies)
    .dependsOn(common)

  val documentSetWorker = OverviewProject("documentset-worker", documentSetWorkerProjectDependencies)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      Keys.fork := true,
      javaOptions in run ++= workerJavaOpts,
      javaOptions in Test ++= Seq("-Dlogback.configurationFile=logback-test.xml")
    )
    .dependsOn(common)
    .dependsOn(commonTest % "test")

  val worker = OverviewProject("worker", workerProjectDependencies)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(packageArchetype.java_application: _*)
    .settings(
      Keys.fork := true,
      javaOptions in run ++= workerJavaOpts,
      javaOptions in Test ++= Seq("-Dlogback.configurationFile=logback-test.xml")
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
          WebJs.JS.Object("name" -> "bundle/admin/Plugin/index"),
          WebJs.JS.Object("name" -> "bundle/admin/User/index"),
          WebJs.JS.Object("name" -> "bundle/ApiToken/index"),
          WebJs.JS.Object("name" -> "bundle/CsvUpload/new"),
          WebJs.JS.Object("name" -> "bundle/DocumentCloudImportJob/new"),
          WebJs.JS.Object("name" -> "bundle/DocumentCloudProject/index"),
          WebJs.JS.Object("name" -> "bundle/DocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/DocumentSet/show"),
          WebJs.JS.Object("name" -> "bundle/FileImport/new"),
          WebJs.JS.Object("name" -> "bundle/PublicDocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/SharedDocumentSet/index"),
          WebJs.JS.Object("name" -> "bundle/Welcome/show")
        ),
        aggregate in Compile := true,
        parallelExecution in IntegrationTest := false,
        javaOptions ++= allJavaOpts ++ devJavaOpts,
        fork := true, // so javaOptions get set
        javaOptions in Test ++= allJavaOpts ++ testJavaOpts ++ Seq(
          "-Dconfig.file=conf/application-test.conf",
          "-Dlogger.resource=logback-test.xml"
        ),
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
