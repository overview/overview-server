import sbt._
import Keys._
import PlayProject._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import templemore.xsbt.cucumber.CucumberPlugin

import org.overviewproject.sbt.assetbundler.AssetBundlerPlugin

object ApplicationBuild extends Build {

  override def settings = super.settings ++
    Seq(EclipseKeys.skipParents in ThisBuild := false)

  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val appDatabaseUrl = "postgres://overview:overview@localhost/overview-dev"
  val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"

  // shared dependencies
  val playDep = "play" %% "play" % "2.0.3"
  val openCsvDep =  "net.sf.opencsv" % "opencsv" % "2.3"
  val postgresqlDep = "postgresql" % "postgresql" % "9.1-901.jdbc4"
  val specs2Dep = "org.specs2" %% "specs2" % "1.11"

  // Project dependencies
  val serverProjectDependencies = Seq(
    openCsvDep,
    postgresqlDep,
    "com.typesafe" %% "play-plugins-mailer" % "2.0.4",
    "org.jodd" % "jodd-wot" % "3.3.4",
    "org.squeryl" %% "squeryl" % "0.9.5-2",
    "ua.t3hnar.bcrypt" % "scala-bcrypt" % "1.4",
    "org.mockito" % "mockito-all" % "1.9.0" % "test"
  )

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonProjectDependencies = Seq(
    playDep,
    postgresqlDep,
    specs2Dep,
    "junit" % "junit" % "4.8.1"
  )

  val workerProjectDependencies = Seq(
    openCsvDep,
    playDep,
    "org.squeryl" %% "squeryl" % "0.9.6-SNAPSHOT",
    specs2Dep % "test",
    "junit" % "junit" % "4.8.1" % "test",
    "org.mockito" % "mockito-all" % "1.9.0" % "test"
  )

  // Project definitions
  val common = Project("common", file("common"), settings =
    Defaults.defaultSettings ++
      Seq(libraryDependencies ++= commonProjectDependencies)
  ).settings(
    testOptions in Test ++= Seq(
      Tests.Argument("xonly"),
      Tests.Setup(() => System.setProperty("datasource.default.url", testDatabaseUrl)))  
  ).settings(parallelExecution in (Test) := false)

  val worker = Project("worker", file("worker"), settings =
    Defaults.defaultSettings ++
      Seq(
        resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        libraryDependencies ++= workerProjectDependencies)
  ).settings(
    testOptions in Test ++= Seq(
      Tests.Argument("xonly"),
      Tests.Setup(() => System.setProperty("datasource.default.url", testDatabaseUrl)))
  ).settings(scalacOptions ++= Seq("-deprecation", "-unchecked")
  ).settings(
    initialize ~= {_ => System.setProperty("datasource.default.url", appDatabaseUrl) }
  ).settings(parallelExecution in (Test) := false).dependsOn(common)

  val main = PlayProject(appName, appVersion, serverProjectDependencies, mainLang = SCALA).settings(
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/",
    resolvers += "scala-bcrypt repo" at "http://nexus.thenewmotion.com/content/repositories/releases-public/",

    // remove Play's asset management--reset to default (see xsbt source code)
    // Left messy after gaining a fatalist view of sbt code
    resourceGenerators in Compile <<= ((definedSbtPlugins in Compile, resourceManaged in Compile) map Defaults.writePluginsDescriptor)(Seq(_)),

    templatesImport += "views.Magic._",
    routesImport += "utils.Binders._"
  ).settings(
    testOptions in Test ++= Seq(
      Tests.Argument("xonly"),
      Tests.Setup({_ =>
        System.setProperty("db.default.url", testDatabaseUrl)
        System.setProperty("mail.from", "sender@example.org")
      })
    )
  ).settings(
    aggregate in Compile := true,
    aggregate in Test := false
  ).settings(
    CucumberPlugin.cucumberSettings : _*
  ).settings(
    AssetBundlerPlugin.assetSettings: _*
  ).settings(
    CucumberPlugin.cucumberFeaturesDir := file("test/features"),
    CucumberPlugin.cucumberStepsBasePackage := "steps",
    (AssetBundlerPlugin.Keys.configFile in (Compile, AssetBundlerPlugin.Keys.assetBundler)) := file("conf/assets.conf")
  ).dependsOn(common).aggregate(worker)

  val all = Project("all", file("all")).aggregate(main,worker)
}
