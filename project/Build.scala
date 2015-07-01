import com.typesafe.sbt.coffeescript.SbtCoffeeScript.autoImport._
import com.typesafe.sbt.digest.SbtDigest.autoImport._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import com.typesafe.sbt.gzip.SbtGzip.autoImport._
import com.typesafe.sbt.jse.SbtJsEngine.autoImport._
import com.typesafe.sbt.less.SbtLess.autoImport._
import com.typesafe.sbt.rjs.SbtRjs.autoImport._
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.SbtWeb.autoImport._
import play.Play.autoImport._
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys
import play.twirl.sbt.Import._
import sbt._
import sbt.Keys._

object ApplicationBuild extends Build {
  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val ourScalaVersion = "2.11.6"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-encoding", "UTF8")

  val ourResolvers = Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
    "FuseSource releases" at "http://repo.fusesource.com/nexus/content/groups/public",
    "More FuseSource" at "http://repo.fusesource.com/maven2/",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
  )

  override def settings = super.settings ++ Seq(
    EclipseKeys.skipParents in ThisBuild := false,
    scalaVersion := ourScalaVersion,
    resolvers ++= ourResolvers
  )

  val allJavaOpts = Seq(
    "-Duser.timezone=UTC"
  )

  val devJavaOpts = Seq(
    "-Ddb.default.dataSource.databaseName=overview-dev",
    "-Ddb.default.dataSource.portNumber=9010"
  )

  val testJavaOpts = Seq(
    "-Ddb.default.dataSource.databaseName=overview-test",
    "-Ddb.default.dataSource.portNumber=9010",
    "-Dlogback.configurationFile=logback-test.xml"
  )

  val workerJavaOpts = Seq(
    "-Dlogback.configurationFile=workerdevlog.xml"
  )

  val ourTestOptions = Seq(
    Tests.Argument(TestFrameworks.Specs2, "xonly"),
    Tests.Argument(TestFrameworks.Specs2, "showtimes"),
    Tests.Argument("junitxml", "console")
  )

  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath =>
    println(classpath.map(_.data).mkString(":"))
  }

  val ourGlobalSettings: Seq[Setting[_]] = (
    Defaults.coreDefaultSettings
    ++ net.virtualvoid.sbt.graph.Plugin.graphSettings
    ++ packageArchetype.java_application
    ++ Seq(
      logBuffered := false, // so Runner gets data sooner
      scalacOptions ++= ourScalacOptions,
      javaOptions ++= allJavaOpts ++ devJavaOpts,
      javaOptions in Test ++= testJavaOpts,
      fork := true, // so javaOptions gets set
      baseDirectory in (Compile,run) := file("."),
      parallelExecution in Test := false,
      aggregate in Test := false,
      testOptions in Test ++= ourTestOptions,
      printClasspath,
      sources in doc in Compile := List()
    )
  )

  lazy val messageBroker = Project("message-broker", file("message-broker"))
    .settings(ourGlobalSettings: _*)
    .settings(
      baseDirectory in (Compile,run) := file("message-broker"),
      scalaVersion := "2.10.5",
      libraryDependencies ++= Dependencies.messageBrokerDependencies
    )

  lazy val searchIndex = Project("search-index", file("search-index"))
    .settings(ourGlobalSettings: _*)
    .settings(
      libraryDependencies ++= Dependencies.searchIndexDependencies,
      javaOptions in run <++= (baseDirectory) map { (d) =>
        Seq(
          "-Des.path.home=" + d,
          "-Xms1g", "-Xmx1g", "-Xss256k",
          "-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC",
          "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly",
          "-Djava.awt.headless=true",
          "-Delasticsearch",
          "-Des.foreground=yes"
        )
      }
    )

  lazy val runner = Project("runner", file("runner"))
    .settings(ourGlobalSettings: _*)
    .settings(libraryDependencies ++= Dependencies.runnerDependencies)

  def dbEvolutionApplierProject(name: String) = Project(name, file("db-evolution-applier"))
    .settings(ourGlobalSettings: _*)
    .settings(
      target := baseDirectory.value / "target" / name, // [error] Overlapping output directories
      libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies,
      mappings in (Compile, packageBin) <++= baseDirectory map { base =>
        val evolutions = ((base / ".." / "conf" / "evolutions") ** "*").get
        evolutions pair relativeTo(base / ".." / "conf")
      }
    )

  lazy val dbEvolutionApplier = dbEvolutionApplierProject("db-evolution-applier")

  // [adamhooper, 2015-07-01] I had a hell of a time convincing
  // db-evolution-applier to run on the test database in one sbt command and on
  // the dev database in another. This is my ugly hack.
  lazy val testDbEvolutionApplier = dbEvolutionApplierProject("test-db-evolution-applier")
    .settings(javaOptions ++= testJavaOpts)

  // Create a subProject with our common settings
  object OverviewProject {
    def apply(name: String, dependencies: Seq[ModuleID]) = Project(name, file(name))
      .settings(ourGlobalSettings: _*)
      .settings(
        unmanagedResourceDirectories in Compile <+= baseDirectory { _ / "../worker-conf" },
        libraryDependencies ++= dependencies
      )
  }

  // Project definitions
  lazy val common = OverviewProject("common", Dependencies.commonDependencies)

  lazy val upgrade20141210MovePages = Project("upgrade-2014-12-10-move-pages", file("upgrade/2014-12-10-move-pages"))
    .settings(ourGlobalSettings: _*)
    .settings(
      resourceDirectory in Compile := (baseDirectory.value / ".." / ".." / "conf"),
      includeFilter in (Compile, resourceDirectory) := "application.conf"
    )
    .dependsOn(common % "test->test;compile->compile")

  lazy val upgrade20150119MoveFiles = Project("upgrade-2015-01-19-move-files", file("upgrade/2015-01-19-move-files"))
    .settings(ourGlobalSettings: _*)
    .settings(
      resourceDirectory in Compile := (baseDirectory.value / ".." / ".." / "conf"),
      includeFilter in (Compile, resourceDirectory) := "application.conf"
    )
    .dependsOn(common % "test->test;compile->compile")

  lazy val upgrade20150615NixUnusedPages = Project("upgrade-2015-06-15-nix-unused-pages", file("upgrade/2015-06-15-nix-unused-pages"))
    .settings(ourGlobalSettings: _*)
    .settings(
      resourceDirectory in Compile := (baseDirectory.value / ".." / ".." / "conf"),
      includeFilter in (Compile, resourceDirectory) := "application.conf"
    )
    .dependsOn(common % "test->test;compile->compile")

  lazy val reindexDocuments = Project("reindex-documents", file("upgrade/reindex-documents"))
    .settings(ourGlobalSettings: _*)
    .settings(libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0")
    .dependsOn(common % "test->test;compile->compile")

  lazy val documentSetWorker = OverviewProject("documentset-worker", Dependencies.documentSetWorkerDependencies)
    .settings(javaOptions in run ++= workerJavaOpts)
    .dependsOn(common % "test->test;compile->compile")

  lazy val worker = OverviewProject("worker", Dependencies.workerDependencies)
    .settings(javaOptions in run ++= workerJavaOpts)
    .dependsOn(common % "test->test;compile->compile")

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.PlayScala)
    .enablePlugins(SbtWeb)
    .settings(ourGlobalSettings: _*)
    .settings(
      version := appVersion,
      libraryDependencies ++= Dependencies.serverDependencies,
      TwirlKeys.templateImports += "views.Magic._",
      RoutesKeys.routesImport += "extensions.Binders._",
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
        WebJs.JS.Object("name" -> "bundle/DocumentSet/show-progress"),
        WebJs.JS.Object("name" -> "bundle/DocumentSetUser/index"),
        WebJs.JS.Object("name" -> "bundle/FileImport/new"),
        WebJs.JS.Object("name" -> "bundle/PublicDocumentSet/index"),
        WebJs.JS.Object("name" -> "bundle/SharedDocumentSet/index"),
        WebJs.JS.Object("name" -> "bundle/Welcome/show")
      ),
      javaOptions in Test ++= Seq(
        "-Dconfig.file=conf/application-test.conf",
        "-Dlogger.resource=logback-test.xml"
      ),
      sources in doc in Compile := List(),
      includeFilter in (Assets, LessKeys.less) := "main.less",
      includeFilter in (TestAssets, CoffeeScriptKeys.coffeescript) := "",
      pipelineStages := Seq(rjs, digest, gzip)
    )
    .dependsOn(common % "test->test;compile->compile")

  lazy val all = Project("all", file("all"))
    .aggregate(main, worker, documentSetWorker, common)
    .settings(
      aggregate in Test := false,
      test in Test <<= (test in Test in main)
        dependsOn (test in Test in worker)
        dependsOn (test in Test in documentSetWorker)
        dependsOn (test in Test in common)
        dependsOn (run in Runtime in testDbEvolutionApplier).toTask("")
    )
}
