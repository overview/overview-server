import com.typesafe.sbt.coffeescript.SbtCoffeeScript.autoImport._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import com.typesafe.sbt.jse.SbtJsEngine.autoImport._
import com.typesafe.sbt.less.SbtLess.autoImport._
import com.typesafe.sbt.rjs.SbtRjs.autoImport._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.SbtWeb.autoImport._
import java.io.IOException
import play.sbt.routes.RoutesCompiler.autoImport._
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys
import play.twirl.sbt.Import._
import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._

object ApplicationBuild extends Build {
  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val ourScalaVersion = "2.11.7"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-encoding", "UTF8")

  val rootDirectory: String = sys.props("user.dir")

  lazy val dockerIp: String = {
    import scala.sys.process._

    // We only evaluate these commands if we're *running*. If we're just
    // compiling, we'll never run these commands. (If we do, we'll get an
    // exception, as is proper -- it's a bug elsewhere in Build.scala.)
    try {
      "docker-machine ip".!!.trim
    } catch {
      case _: IOException => {
        Seq("docker", "inspect", "-f", "{{ .NetworkSettings.Gateway }}", "overview-dev-database").!!.trim
      }
    }
  }

  val ourResolvers = Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "jbig2 repository" at "http://jbig2-imageio.googlecode.com/svn/maven-repository",
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
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

  lazy val devJavaOpts = allJavaOpts ++ Seq(
    "-Ddb.default.dataSource.databaseName=overview-dev",
    "-Ddb.default.dataSource.portNumber=9010",
    s"-Ddb.default.dataSource.serverName=$dockerIp",
    s"-Dsearch_index.hosts=$dockerIp:9200",
    s"-Dredis.host=$dockerIp",
    "-Dredis.port=9020",
    s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/dev"
  )

  lazy val testJavaOpts = allJavaOpts ++ Seq(
    "-Ddb.default.dataSource.databaseName=overview-test",
    "-Ddb.default.dataSource.portNumber=9010",
    s"-Ddb.default.dataSource.serverName=$dockerIp",
    s"-Dsearch_index.hosts=$dockerIp:9201",
    s"-Dredis.host=$dockerIp",
    "-Dredis.port=9020",
    "-Dlogback.configurationFile=logback-test.xml",
    s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/test"
  )

  val ourTestOptions = Seq(
    Tests.Argument(TestFrameworks.Specs2, "xonly"),
    Tests.Argument(TestFrameworks.Specs2, "showtimes"),
    Tests.Argument("junitxml", "console")
  )

  val ourGlobalSettings: Seq[Setting[_]] = (
    Defaults.coreDefaultSettings
    ++ Seq(
      logBuffered := false, // so Runner gets data sooner
      scalacOptions ++= ourScalacOptions,
      javaOptions in Compile ++= devJavaOpts,
      javaOptions in Test ++= testJavaOpts,
      fork := true, // so javaOptions gets set
      parallelExecution in Test := false,
      aggregate in Test := false,
      testOptions in Test ++= ourTestOptions,
      sources in doc in Compile := List()
    )
  )

  def project(name: String) = Project(name, file(name))
    .settings(ourGlobalSettings: _*)
    .enablePlugins(JavaAppPackaging)

  def dbEvolutionApplierProject(name: String) = Project(name, file("db-evolution-applier"))
    .settings(ourGlobalSettings: _*)
    .enablePlugins(JavaAppPackaging)
    .settings(
      target := baseDirectory.value / "target" / name, // [error] Overlapping output directories
      libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" / "db"
    )

  lazy val dbEvolutionApplier = dbEvolutionApplierProject("db-evolution-applier")

  // [adamhooper, 2015-07-01] I had a hell of a time convincing
  // db-evolution-applier to run on the test database in one sbt command and on
  // the dev database in another. This is my ugly hack.
  lazy val testDbEvolutionApplier = dbEvolutionApplierProject("test-db-evolution-applier")
    .settings(javaOptions in Compile ++= testJavaOpts)

  // Project definitions
  lazy val common = project("common")
    .settings(libraryDependencies ++= Dependencies.commonDependencies)

  lazy val reindexDocuments = Project("reindex-documents", file("upgrade/reindex-documents"))
    .settings(ourGlobalSettings: _*)
    .enablePlugins(JavaAppPackaging)
    .settings(libraryDependencies ++= Dependencies.reindexDependencies)

  // Worker config includes setup for worker/re-start used by ./dev
  // We must set the javaOptions in Revolver.reStart or everything fails when
  // developing under docker-machine as search_index.hosts is not set correctly
  lazy val worker = project("worker")
    .settings(Revolver.settings)
    .settings(
      libraryDependencies ++= Dependencies.workerDependencies,
      javaOptions in Test += "-Dconfig.resource=test.conf",
      mainClass in Compile := Some("com.overviewdocs.Worker"),
      javaOptions in Revolver.reStart ++= devJavaOpts
    )
    .dependsOn(common % "test->test;compile->compile")

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala)
    .enablePlugins(SbtWeb)
    .enablePlugins(JavaAppPackaging)
    .settings(ourGlobalSettings: _*)
    .settings(
      version := appVersion,
      routesGenerator := StaticRoutesGenerator,
      PlayKeys.externalizeResources := false, // so `stage` doesn't nix all assets
      libraryDependencies ++= Dependencies.serverDependencies,
      TwirlKeys.templateImports += "views.Magic._",
      RoutesKeys.routesImport += "extensions.Binders._",
      RjsKeys.modules := Seq(
        WebJs.JS.Object("name" -> "bundle/admin/Job/index"),
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
      javaOptions in Test += "-Dlogger.resource=logback-test.xml",
      sources in doc in Compile := List(),
      includeFilter in (Assets, LessKeys.less) := "main.less",
      includeFilter in (TestAssets, CoffeeScriptKeys.coffeescript) := "",
      // RJS seems to kill the rest of sbt-web for us. TODO figure this out, so
      // we can use versioned assets. See
      // https://groups.google.com/d/topic/play-framework/3DiknO8OGK4/discussion
      //
      // ... the difference between us and Play's docs: *we* use RjsKeys.modules
      // (The Play docs call for Seq(rjs, digest, gzip), which is exactly what
      // we want.)
      pipelineStages := Seq(rjs)
    )
    .dependsOn(common % "test->test;compile->compile;test->compile")

  lazy val all = Project("all", file("all"))
    .aggregate(main, worker, common)
    .settings(
      aggregate in Test := false,
      test in Test <<= (test in Test in main)
        dependsOn (test in Test in worker)
        dependsOn (test in Test in common)
        dependsOn (run in Runtime in testDbEvolutionApplier).toTask("")
    )
}
