import sbt._
import Keys._
import PlayProject._
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

object ApplicationBuild extends Build {

    override def settings = super.settings ++ 
      Seq(EclipseKeys.skipParents in ThisBuild := false)

    val appName         = "overview-server"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
        "postgresql" % "postgresql" % "9.1-901.jdbc4",
        "net.sf.opencsv" % "opencsv" % "2.3"
    )

    
    val common = PlayProject("overview-common", appVersion, appDependencies, path = file("common"), mainLang = JAVA)
    
    val worker = Project("overview-worker", file("worker"), settings =
      Defaults.defaultSettings ++ 
      Seq(libraryDependencies ++= appDependencies ++ 
      							  Seq("play" %% "play" % "2.0.2") ++  
         						  Seq("org.specs2" %% "specs2" % "1.11" % "test"))).dependsOn(common).aggregate(common)
         						 

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(
      // Add your own project settings here      
    ).dependsOn(common, worker).aggregate(common,worker)

}
