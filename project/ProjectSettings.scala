import sbt._
import play.Keys._

trait ProjectSettings {
  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val ourScalaVersion = "2.10.3"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.7", "-encoding", "UTF8")

  val appDatabaseUrl = "postgres://overview:overview@localhost:9010/overview-dev"
  val testDatabaseUrl	= "postgres://overview:overview@localhost:9010/overview-test"

  val ourResolvers = Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
    "FuseSource releases" at "http://repo.fusesource.com/nexus/content/groups/public",
    "More FuseSource" at "http://repo.fusesource.com/maven2/",
    "PDFBox Snapshot" at "https://repository.apache.org/content/groups/snapshots"
  )
    
  // shared dependencies
  val akkaAgentDep = "com.typesafe.akka" %% "akka-agent" % "2.2.0"
  val akkaDep = "com.typesafe.akka" %% "akka-actor" % "2.2.0"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"  % "2.2.0"
  val asyncHttpClientDep = "com.ning" % "async-http-client" % "1.7.18"
  val boneCpDep = "com.jolbox" % "bonecp" % "0.8.0.RELEASE"
  val elasticSearchCloudAwsDep = "org.elasticsearch" % "elasticsearch-cloud-aws" % "1.12.0"
  val elasticSearchDep = "org.elasticsearch" % "elasticsearch" % "0.90.2"
  val geronimoJmsDep = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.0" // javax.jms
  val guavaDep = "com.google.guava" % "guava" % "16.0"
  val javaxMailDep = "javax.mail" % "mail" % "1.4.1"
  val junitDep = "junit" % "junit-dep" % "4.11"
  val junitInterfaceDep = "com.novocode" % "junit-interface" % "0.9"
  val logbackDep = "ch.qos.logback" % "logback-classic" % "1.0.9"
  val mockitoDep = "org.mockito" % "mockito-all" % "1.9.5"
  val openCsvDep = "net.sf.opencsv" % "opencsv" % "2.3"
  val playJsonDep = "com.typesafe.play" %% "play-json" % play.core.PlayVersion.current
  val postgresqlDep = "postgresql" % "postgresql" % "9.1-901.jdbc4"
  val scalaArmDep = "com.jsuereth" %% "scala-arm" % "1.3"
  val specs2Dep = "org.specs2" %% "specs2" % "2.3.4"
  val squerylDep = "org.squeryl" %% "squeryl" % "0.9.6-RC2"
  val stompDep = "org.fusesource.stompjms" % "stompjms-client" % "1.15"

  // WHY doesn't this work? I can't seem to refer to sbt.ModuleId here.
  // 
  // And come to that, WHY doesn't Ivy work?
  // http://stackoverflow.com/questions/6158192/apache-ivy-error-message-impossible-to-get-artifacts-when-data-has-not-been-l
  //def guavaSafeDependencies(dependencies: Seq[sbt.ModuleId]) = {
  //  Seq(guavaDep) ++ dependencies.map(_.exclude("com.google.guava", "guava"))
  //}

  // Project dependencies
  val serverProjectDependencies = Seq(guavaDep) ++ (Seq(
    jdbc,
    filters,
    openCsvDep,
    "com.typesafe" %% "play-plugins-util" % "2.2.0",
    "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
    "com.github.t3hnar" %% "scala-bcrypt" % "2.2",
    "org.owasp.encoder" % "encoder" % "1.1",
    "org.jodd" % "jodd-wot" % "3.3.8" % "test",
    "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current % "test"
  )).map(_.exclude("com.google.guava", "guava"))

  val dbEvolutionApplierDependencies = Seq(guavaDep) ++ (Seq(
    jdbc,
    postgresqlDep
  )).map(_.exclude("com.google.guava", "guava"))

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonProjectDependencies = Seq(guavaDep) ++ (Seq(
    akkaDep,
    asyncHttpClientDep,
    boneCpDep,
    geronimoJmsDep,
    logbackDep,
    playJsonDep,
    postgresqlDep,
    squerylDep,
    stompDep,
    anorm % "test",
    specs2Dep % "test",
    junitInterfaceDep % "test",
    akkaTestkit % "test",
    mockitoDep % "test",
    junitDep % "test"
  )).map(_.exclude("com.google.guava", "guava"))

  val commonTestProjectDependencies = Seq(guavaDep) ++ (Seq(
    akkaDep,
    akkaTestkit,
    anorm,
    logbackDep,
    specs2Dep,
    junitInterfaceDep,
    junitDep,
    mockitoDep
  )).map(_.exclude("com.google.guava", "guava"))

  val workerProjectDependencies = Seq(guavaDep) ++ (Seq(
    javaxMailDep, 
    openCsvDep,
    anorm,
    elasticSearchDep,
    elasticSearchCloudAwsDep
  )).map(_.exclude("com.google.guava", "guava"))
  
  val documentSetWorkerProjectDependencies = Seq(guavaDep) ++ (Seq(
    akkaAgentDep,
    logbackDep,
    elasticSearchDep,
    elasticSearchCloudAwsDep,
    javaxMailDep,
    "org.apache.pdfbox" % "pdfbox" % "1.8.6-SNAPSHOT",
    "org.bouncycastle" % "bcprov-jdk15" % "1.44",
    "org.bouncycastle" % "bcmail-jdk15" % "1.44"
  )).map(_.exclude("com.google.guava", "guava"))
  
  val messageBrokerDependencies = Seq(
    "org.apache.activemq" % "apache-apollo" % "1.6",
    javaxMailDep,
    // message-broker relies on scala-compiler, but a different version than
    // Squeryl does. That means two huge, useless jars in the distribution
    // instead of one. Require the latest version, so that the jars aren't
    // duplicated.
    "org.scala-lang" % "scala-compiler" % ourScalaVersion
  )

  val searchIndexDependencies = Seq(
    "log4j" % "log4j" % "1.2.17",
    elasticSearchDep,
    elasticSearchCloudAwsDep
  )

  val runnerDependencies = Seq(
    specs2Dep % "test",
    mockitoDep % "test",
    scalaArmDep,
    postgresqlDep,
    "org.rogach" %% "scallop" % "0.9.4"
  )
}
