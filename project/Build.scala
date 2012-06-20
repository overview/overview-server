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
   	"postgresql" % "postgresql" % "9.1-901.jdbc4"
    )

    val worker = Project("overview-worker", 
    file("worker"), settings =
     Defaults.defaultSettings ++ 
     Seq(libraryDependencies ++= appDependencies ++ Seq("play" %% "play" % "2.0.1")))

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(
      // Add your own project settings here      
    ).dependsOn(worker).aggregate(worker)

}
