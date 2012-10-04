import sbt._
import Keys._
import PlayProject._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import templemore.xsbt.cucumber.CucumberPlugin

object ApplicationBuild extends Build {

  override def settings = super.settings ++
    Seq(EclipseKeys.skipParents in ThisBuild := false)

  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val appDatabaseUrl = "postgres://overview:overview@localhost/overview-dev"
  val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"

  val appDependencies = Seq(
    "org.squeryl" %% "squeryl" % "0.9.5-2",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "org.mockito" % "mockito-all" % "1.9.0",
    "org.jodd" % "jodd-wot" % "3.3.4"
  )

  val playAppDependencies = appDependencies ++ Seq(
    "net.sf.opencsv" % "opencsv" % "2.3",
    "jp.t2v" %% "play20.auth" % "0.3",
    "ua.t3hnar.bcrypt" % "scala-bcrypt" % "1.4",
    "com.typesafe" %% "play-plugins-mailer" % "2.0.4"
  )

  def customLessEntryPoints(base: File) : PathFinder = (
    (base / "app" / "assets" / "stylesheets" ** "main.less") +++
    (base / "app" / "assets" / "stylesheets" ** "DV.less")
  )


  val worker = Project("overview-worker", file("worker"), settings =
    Defaults.defaultSettings ++ 
      Seq(libraryDependencies ++= 
        appDependencies ++
        Seq("play" %% "play" % "2.0.3") ++
        Seq("org.specs2" %% "specs2" % "1.11" % "test"))
      ).settings(
        testOptions in Test ++= Seq(
          Tests.Argument("xonly"), 
          Tests.Setup(() => System.setProperty("datasource.default.url", testDatabaseUrl)))
      ).settings(scalacOptions ++= Seq("-deprecation", "-unchecked")
      ).settings(
        initialize ~= {_ => System.setProperty("datasource.default.url", appDatabaseUrl) }
      ).settings(parallelExecution in (Test) := false)


  val main = PlayProject(appName, appVersion, playAppDependencies, mainLang = SCALA).settings(
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/",
    resolvers += "scala-bcrypt repo" at "http://nexus.thenewmotion.com/content/repositories/releases-public/",         
    lessEntryPoints <<= baseDirectory(customLessEntryPoints),
    templatesImport += "views.Magic._"
  ).settings(
    testOptions in Test ++= Seq(
      Tests.Argument("xonly"),
      Tests.Setup({_ =>
        System.setProperty("db.default.url", testDatabaseUrl)
        System.setProperty("mail.from", "sender@example.org")
      })
    )
  ).settings(
    CucumberPlugin.cucumberSettings : _*
  ).settings(
    CucumberPlugin.cucumberFeaturesDir := file("test/features"),
    CucumberPlugin.cucumberStepsBasePackage := "steps"
  ).dependsOn(worker).aggregate(worker)
}
