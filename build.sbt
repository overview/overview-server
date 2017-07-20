import java.io.IOException

val rootDirectory = sys.props("user.dir")

lazy val dockerIp: String = {
  import scala.sys.process._

  // We only evaluate these commands if we're *running*. If we're just
  // compiling, we'll never run these commands.
  try {
    "docker-machine ip".!!.trim
  } catch {
    case _: IOException => {
      Seq("docker", "inspect", "-f", "{{ .NetworkSettings.Gateway }}", "overview-dev-database").!!.trim
    }
  }
}

val baseJavaOpts = Seq(
  "-Duser.timezone=UTC"
)

lazy val devJavaOpts = baseJavaOpts ++ Seq(
  s"-Ddb.default.properties.databaseName=overview-dev",
  s"-Ddb.default.properties.portNumber=9010",
  s"-Ddb.default.properties.serverName=$dockerIp",
  s"-Dredis.host=$dockerIp",
  s"-Dredis.port=9020",
  s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/dev",
  s"-Dsearch.baseDirectory=$rootDirectory/search/dev"
)

lazy val testJavaOpts = baseJavaOpts ++ Seq(
  s"-Ddb.default.properties.databaseName=overview-test",
  s"-Ddb.default.properties.portNumber=9010",
  s"-Ddb.default.properties.serverName=$dockerIp",
  s"-Dredis.host=$dockerIp",
  s"-Dredis.port=9020",
  s"-Dlogback.configurationFile=logback-test.xml",
  s"-DblobStorage.file.baseDirectory=$rootDirectory/blob-storage/test",
  s"-Dsearch.baseDirectory=$rootDirectory/search/test"
)

// Settings that apply to all our projects
lazy val root = (project in file("."))
  .settings(
    inThisBuild(List(
      // Settings common to all projects
      organization := "com.overviewdocs",
      version := "1.0.0-SNAPSHOT",
      scalaVersion := "2.12.2",
      scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-encoding", "UTF8"),
      resolvers := Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        "jbig2 repository" at "http://jbig2-imageio.googlecode.com/svn/maven-repository",
        "Oracle Released Java Packages" at "http://download.oracle.com/maven",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      ),
      logBuffered := false,
      javaOptions in Compile := devJavaOpts,
      javaOptions in Test := testJavaOpts,
      fork := true, // so javaOptions gets set
      parallelExecution in Test := false, // because we test with the (global) database
      aggregate in Test := false, // otherwise we'd get parallel execution
      testOptions in Test := Seq(
        Tests.Argument(TestFrameworks.Specs2, "xonly"),
        Tests.Argument(TestFrameworks.Specs2, "showtimes"),
        Tests.Argument("junitxml", "console")
      )
    )),
    publish := (),
    publishLocal := ()
  )

// Runs database evolutions on prod+dev
lazy val `db-evolution-applier` = (project in file("db-evolution-applier"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    target := baseDirectory.value / "target" / "db-evolution-applier", // [error] Overlapping output directories
    libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies
  )

// Runs database evolutions on test (run this before running other tests)
lazy val `test-db-evolution-applier` = (project in file("db-evolution-applier"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    target := baseDirectory.value / "target" / "test-db-evolution-applier", // [error] Overlapping output directories
    javaOptions in Compile := testJavaOpts,
    libraryDependencies ++= Dependencies.dbEvolutionApplierDependencies
  )

// Shared code for other projects
lazy val common = (project in file("common"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    libraryDependencies ++= Dependencies.commonDependencies
  )

// Daemon that executes background tasks
lazy val worker = (project in file("worker"))
  .enablePlugins(JavaAppPackaging)
  //.enablePlugins(Revolver)
  .settings(
    libraryDependencies ++= Dependencies.workerDependencies,
    javaOptions in Test += "-Dconfig.resource=test.conf",
    mainClass in Compile := Some("com.overviewdocs.Worker"),
    sources in doc in Compile := List(), // docs take time; skip 'em
    javaOptions in reStart ++= devJavaOpts
  )
  .dependsOn(common % "test->test;compile->compile")

// Daemon that communicates with web browsers
lazy val web = (project in file("web"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .enablePlugins(JavaAppPackaging)
  .settings(
    PlayKeys.externalizeResources := false, // so `stage` doesn't nix all assets
    libraryDependencies ++= Dependencies.serverDependencies,
    TwirlKeys.templateImports ++= Seq("views.Magic._", "play.twirl.api.HtmlFormat"),
    routesImport += "extensions.Binders._",
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
    sources in doc in Compile := List(), // docs take time; skip 'em
    includeFilter in (Assets, LessKeys.less) := "main.less",
    includeFilter in (TestAssets, CoffeeScriptKeys.coffeescript) := "",
    pipelineStages := Seq(rjs, digest, gzip)
  )
  .dependsOn(common % "test->test;compile->compile;test->compile")

// Test aggregator. Call as "./sbt all/test".
lazy val all = (project in file("all"))
  .aggregate(web, worker, common)
  .settings(
    test in Test := Def.sequential(
      (run in Runtime in `test-db-evolution-applier`).toTask(""),
      test in Test in common,
      test in Test in worker,
      test in Test in web
    ).value
  )
