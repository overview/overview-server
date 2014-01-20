import sbt._
import sbt.PlayKeys._

trait ProjectSettings {
  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val ourScalaVersion = "2.10.0"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature")

  val appDatabaseUrl = "postgres://overview:overview@localhost/overview-dev"
  val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"
    
    

  val ourResolvers = Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
    "FuseSource releases" at "http://repo.fusesource.com/nexus/content/groups/public",
    "More FuseSource" at "http://repo.fusesource.com/maven2/"
  )
    
  // shared dependencies
  val openCsvDep =  "net.sf.opencsv" % "opencsv" % "2.3"
  val postgresqlDep = "postgresql" % "postgresql" % "9.1-901.jdbc4"
  val specs2Dep = "org.specs2" %% "specs2" % "1.14"
  val squerylDep = "org.squeryl" %% "squeryl" % "0.9.6-SNAPSHOT"
  val mockitoDep = "org.mockito" % "mockito-all" % "1.9.5"
  val junitInterfaceDep = "com.novocode" % "junit-interface" % "0.9"
  val junitDep = "junit" % "junit-dep" % "4.11"
  val saddleDep = "org.scala-saddle" %% "saddle" % "1.0.+"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"  % "2.1.0"
  val geronimoJmsDep = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.0"
  val stompDep = "org.fusesource.stompjms" % "stompjms-client" % "1.15"
  val logbackDep = "ch.qos.logback" % "logback-classic" % "1.0.9"
  val javaxMailDep = "javax.mail" % "mail" % "1.4.1"
  val elasticSearchDep = "org.elasticsearch" % "elasticsearch" % "0.90.2"
  val elasticSearchCloudAwsDep = "org.elasticsearch" % "elasticsearch-cloud-aws" % "1.12.0"
  val guavaDep = "com.google.guava" % "guava" % "15.0"
  
  // Project dependencies
  val serverProjectDependencies = Seq(
    jdbc,
    filters,
    geronimoJmsDep,
    openCsvDep,
    postgresqlDep,
    squerylDep,
    stompDep,
    mockitoDep % "it,test",
    elasticSearchDep % "it",
    "com.typesafe" %% "play-plugins-util" % "2.1.0",
    "com.typesafe" %% "play-plugins-mailer" % "2.1.0",
    "com.github.t3hnar" %% "scala-bcrypt" % "2.2",
    "org.owasp.encoder" % "encoder" % "1.1",
    "org.jodd" % "jodd-wot" % "3.3.1" % "it,test",
    "play" %% "play-test" % play.core.PlayVersion.current % "it,test",
    "com.icegreen" % "greenmail" % "1.3.1b" % "it",
    "org.seleniumhq.selenium" % "selenium-java" % "2.35.0" % "it" // Play 2.1.0's is too old, doesn't work with newer Firefox
  )

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonProjectDependencies = Seq(
    jdbc,
    anorm,
    postgresqlDep,
    squerylDep,
    guavaDep,
    specs2Dep, // FIXME add % "test"
    junitInterfaceDep, // FIXME add % "test"
    junitDep // FIXME add % "test"
  )

  val workerProjectDependencies = Seq(
    javaxMailDep, 
    jdbc,
    openCsvDep,
    squerylDep,
    elasticSearchDep,
    elasticSearchCloudAwsDep,
    akkaTestkit % "test",
    mockitoDep % "test",
    specs2Dep % "test",
    junitInterfaceDep, // FIXME add % "test"
    junitDep % "test",
    saddleDep
  )

  val workerCommonProjectDependencies = Seq(
    jdbc, //  this brings out Play components used in worker: asynchttpclient and json parsing
    akkaTestkit,
    specs2Dep,    
    geronimoJmsDep,
    squerylDep,
    stompDep,
    mockitoDep % "test"
  )
  
  val documentSetWorkerProjectDependencies = Seq(
    elasticSearchDep,
    elasticSearchCloudAwsDep,
    javaxMailDep,
    jdbc,
    geronimoJmsDep,
    stompDep,
    squerylDep,
    "org.apache.pdfbox" % "pdfbox" % "1.8.2",
    "org.bouncycastle" % "bcprov-jdk15" % "1.44",
    "org.bouncycastle" % "bcmail-jdk15" % "1.44",
    akkaTestkit % "test",
    specs2Dep % "test",
    mockitoDep % "test"
  )
  
  val messageBrokerDependencies = Seq(
    "org.apache.activemq" % "apache-apollo" % "1.6",
    javaxMailDep
  )

  val searchIndexDependencies = Seq(
    "log4j" % "log4j" % "1.2.17",
    elasticSearchDep,
    elasticSearchCloudAwsDep
  )

  val runnerDependencies = Seq(
    akkaTestkit % "test",
    specs2Dep % "test",
    mockitoDep % "test",
    "org.rogach" %% "scallop" % "0.9.4"
  )
}
