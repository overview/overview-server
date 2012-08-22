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

  val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"

  val appDependencies = Seq(
    "org.squeryl" %% "squeryl" % "0.9.5-2",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "org.mockito" % "mockito-all" % "1.9.0",
    "org.jodd" % "jodd-wot" % "3.3.4"
  )

  val playAppDependencies = appDependencies ++ Seq(
    "net.sf.opencsv" % "opencsv" % "2.3",
    "jp.t2v" %% "play20.auth" % "0.3-SNAPSHOT"
  )

  val common = PlayProject("overview-common", appVersion, appDependencies, path = file("common"), mainLang = JAVA).settings(
    testOptions in Test += Tests.Setup( () =>
      System.setProperty("db.default.url", testDatabaseUrl ))
  )

  val worker = Project("overview-worker", file("worker"), settings =
    Defaults.defaultSettings ++
      Seq(libraryDependencies ++= 
        appDependencies ++
        Seq("play" %% "play" % "2.0.3") ++
        Seq("org.specs2" %% "specs2" % "1.11" % "test"))
      ).settings(scalacOptions ++= Seq("-deprecation", "-unchecked")).
        settings(
          testOptions in Test += Tests.Setup( () =>
          	System.setProperty("datasource.default.url", testDatabaseUrl))
      )

  val main = PlayProject(appName, appVersion, playAppDependencies, mainLang = SCALA).settings(
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/",

    testOptions in Test += Tests.Setup( () =>
      System.setProperty("db.default.url", testDatabaseUrl)
    ),
    templatesImport += "views.Magic._"
  ).settings(
    CucumberPlugin.cucumberSettings : _*
  ).settings(
    CucumberPlugin.cucumberFeaturesDir := file("test/features"),
    CucumberPlugin.cucumberStepsBasePackage := "steps"
  ).dependsOn(common, worker).aggregate(common,worker)

}
