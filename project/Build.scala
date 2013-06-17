import sbt._
import Keys._
import play.Project._
import templemore.sbt.cucumber.CucumberPlugin
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import com.typesafe.sbt.SbtStartScript


object ApplicationBuild extends Build with ProjectSettings {
  override def settings = super.settings ++ Seq(
    EclipseKeys.skipParents in ThisBuild := false,
    scalaVersion := ourScalaVersion,
    resolvers ++= ourResolvers
  )

  
  val ourTestWithNoDbOptions = Seq(
    Tests.Argument("xonly")    
  )
    
  val ourTestOptions = ourTestWithNoDbOptions ++ Seq(
    Tests.Setup(() => System.setProperty("datasource.default.url", testDatabaseUrl)),
    Tests.Setup(loader => ClearTestDatabase(loader))
  )
  
  
  val printClasspathTask = TaskKey[Unit]("print-classpath")
  val printClasspath = printClasspathTask <<= (fullClasspath in Runtime) map { classpath => 
    println(classpath.map(_.data).mkString(":"))
  }
  
  val messageBroker = Project("message-broker", file("message-broker"), settings = 
    Defaults.defaultSettings ++ SbtStartScript.startScriptForClassesSettings ++ OverviewCommands.defaultSettings ++
      Seq(     
        libraryDependencies ++=  messageBrokerDependencies,
        printClasspath))

  
  
  
  // Create a subProject with our common settings
  object OverviewProject extends OverviewCommands with OverviewKeys {
    def apply(name: String, dependencies: Seq[ModuleID], 
      theTestOptions: Seq[TestOption] = ourTestOptions) = {
      Project(name, file(name), settings = 
        Defaults.defaultSettings ++
        defaultSettings ++
	SbtStartScript.startScriptForClassesSettings ++
	Seq(printClasspath) ++
	Seq(        
          libraryDependencies ++= dependencies,
          testOptions in Test ++= theTestOptions,
          scalacOptions ++= ourScalacOptions,
          unmanagedResourceDirectories in Compile <+= baseDirectory { _ / "../worker-conf" },
          logBuffered := false,
          parallelExecution in Test := false,
          sources in doc in Compile := List(),
          printClasspath
        )
      )
    }
    
    // don't clean the database if it isn't being used in tests
    def withNoDbTests(name: String, dependencies: Seq[ModuleID], 
      theTestOptions: Seq[TestOption] = ourTestWithNoDbOptions) = apply(name, dependencies, theTestOptions)
  }
  
  
  // Project definitions
  val common = OverviewProject("common", commonProjectDependencies)
  
  val workerCommon = OverviewProject.withNoDbTests("worker-common", workerCommonProjectDependencies)
  
  val documentSetWorker = OverviewProject.withNoDbTests("documentset-worker", documentSetWorkerProjectDependencies)
    .dependsOn(common, workerCommon)
  
  
  val worker = OverviewProject("worker", workerProjectDependencies).settings(
    initialize ~= { _ =>
      if (System.getProperty("datasource.default.url") == null) {
        System.setProperty("datasource.default.url", appDatabaseUrl)
      }
    }
  ).dependsOn(workerCommon, common)

  val main = play.Project(appName, appVersion, serverProjectDependencies).settings(
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/",
    resolvers += "scala-bcrypt repo" at "http://nexus.thenewmotion.com/content/repositories/releases-public/"
  ).settings(
    CucumberPlugin.cucumberSettingsWithIntegrationTestPhaseIntegration : _*
  ).configs(
    IntegrationTest
  ).settings(
    Defaults.itSettings : _*
  ).settings(
    scalacOptions ++= ourScalacOptions,
    templatesImport += "views.Magic._",
    lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" * "*.less"), // only compile .less files that aren't in subdirs
    requireJs ++= Seq(
      "bundle/DocumentCloudImportJob/new.js",
      "bundle/DocumentSet/index.js",
      "bundle/DocumentSet/show.js",
      "bundle/Document/show.js",
      "bundle/Welcome/show.js"
    ),
    requireJsShim += "main.js",
    aggregate in Compile := true,
    parallelExecution in IntegrationTest := false,
    javaOptions in Test ++= Seq(
      "-Dconfig.file=conf/application-test.conf",
      "-Dlogger.resource=logback-test.xml",
      "-Ddb.default.url=" + testDatabaseUrl
    ),
    javaOptions in IntegrationTest ++= Seq(
      "-Dconfig.file=conf/application-it.conf",
      "-Dlogger.resource=logback-test.xml",
      "-Ddb.default.url=" + testDatabaseUrl,
      "-Dsbt.ivy.home=" + sys.props("sbt.ivy.home"),
      "-Dsbt.boot.properties=" + sys.props("sbt.boot.properties"),
      "-Dplay.home=" + sys.props("play.home"),
      "-Ddatasource.default.url=" + testDatabaseUrl
    ),
    Keys.fork in Test := true,
    aggregate in Test := false,
    testOptions in Test ++= ourTestOptions,
    logBuffered := false,
    Keys.fork in IntegrationTest := true,
    sources in doc in Compile := List(),
    printClasspath,
    aggregate in printClasspathTask := false
  ).dependsOn(common)


  val all = Project("all", file("all"))
    .aggregate(main, worker, documentSetWorker, workerCommon, common)
    .settings(
        aggregate in Test := false,
      test in Test <<= (test in Test in main) 
        dependsOn (test in Test in worker)
        dependsOn (test in Test in documentSetWorker)
        dependsOn (test in Test in workerCommon)
        dependsOn (test in Test in common)
    )
          

}
