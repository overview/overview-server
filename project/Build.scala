import sbt._
import Keys._
import PlayProject._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

object ApplicationBuild extends Build {

    override def settings = super.settings ++ 
      Seq(EclipseKeys.skipParents in ThisBuild := false)

    val appName         = "overview-server"
    val appVersion      = "1.0-SNAPSHOT"

    val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"
      
    val appDependencies = Seq(
        "org.squeryl" %% "squeryl" % "0.9.5-2",
        "postgresql" % "postgresql" % "9.1-901.jdbc4",
        "net.sf.opencsv" % "opencsv" % "2.3",
        "org.mockito" % "mockito-all" % "1.9.0" 
    )

    
    val common = PlayProject("overview-common", appVersion, appDependencies, path = file("common"), mainLang = JAVA).settings(
      testOptions in Test += Tests.Setup( () => 
      System.setProperty("db.default.url", testDatabaseUrl ))
    )
    
    
    val worker = Project("overview-worker", file("worker"), settings =
      Defaults.defaultSettings ++ 
      Seq(libraryDependencies ++= appDependencies ++ 
      							  Seq("play" %% "play" % "2.0.2") ++  
         						  Seq("org.specs2" %% "specs2" % "1.11" % "test"))).dependsOn(common).aggregate(common)
         						 

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      testOptions in Test += Tests.Setup( () => 
      System.setProperty("db.default.url", testDatabaseUrl))  
    ).dependsOn(common, worker).aggregate(common,worker)

}
