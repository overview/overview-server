import sbt._
import play.Play.autoImport._
import play.sbt.PlayImport._

trait ProjectSettings {
  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val ourScalaVersion = "2.11.6"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature", "-target:jvm-1.8", "-encoding", "UTF8")

  val ourResolvers = Seq(
    Resolver.url("Edulify repository", url("http://edulify.github.io/modules/releases/"))(Resolver.ivyStylePatterns),
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
    "FuseSource releases" at "http://repo.fusesource.com/nexus/content/groups/public",
    "More FuseSource" at "http://repo.fusesource.com/maven2/",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
  )
    
  // shared dependencies
  val akkaAgentDep = "com.typesafe.akka" %% "akka-agent" % "2.3.4"
  val akkaDep = "com.typesafe.akka" %% "akka-actor" % "2.3.4"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"  % "2.3.4"
  val awsCoreDep = "com.amazonaws" % "aws-java-sdk-core" % "1.9.23"
  val awsS3Dep = "com.amazonaws" % "aws-java-sdk-s3" % "1.9.23"
  val asyncHttpClientDep = "com.ning" % "async-http-client" % "1.7.18"
  val commonsIoDep = "commons-io" % "commons-io" % "2.4"
  val elasticSearchDep = "org.elasticsearch" % "elasticsearch" % "1.4.2"
  val geronimoJmsDep = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.0" // javax.jms
  val guavaDep = "com.google.guava" % "guava" % "18.0"
  val hikariCpDep = "com.zaxxer" % "HikariCP" % "2.3.8"
  val javaxMailDep = "javax.mail" % "mail" % "1.4.7"
  val junitDep = "junit" % "junit-dep" % "4.11"
  val junitInterfaceDep = "com.novocode" % "junit-interface" % "0.9"
  val logbackDep = "ch.qos.logback" % "logback-classic" % "1.0.9"
  val mockitoDep = "org.mockito" % "mockito-all" % "1.9.5"
  val openCsvDep = "com.opencsv" % "opencsv" % "3.4"
  val playJsonDep = "com.typesafe.play" %% "play-json" % play.core.PlayVersion.current
  val playStreamsDep = "com.typesafe.play" %% "play-streams-experimental" % play.core.PlayVersion.current
  val postgresqlDep = "org.postgresql" % "postgresql" % "9.3-1103-jdbc41"
  val pgSlickDep = "com.github.tminglei" %% "slick-pg" % "0.9.0"
  val redisDep = "net.debasishg" %% "redisreact" % "0.6"
  val scalaArmDep = "com.jsuereth" %% "scala-arm" % "1.4"
  val slickDep = "com.typesafe.slick" %% "slick" % "3.0.0"
  val specs2Dep = "org.specs2" %% "specs2" % "2.3.13"
  val squerylDep = "org.squeryl" %% "squeryl" % "0.9.6-RC3"
  val stompDep = "org.fusesource.stompjms" % "stompjms-client" % "1.15"

  // Project dependencies
  val serverProjectDependencies = Seq(
    ws,
    filters,
    openCsvDep,
    slickDep,
    playStreamsDep,
    "com.typesafe.play.plugins" %% "play-plugins-util" % "2.3.0",
    "com.typesafe.play" %% "play-mailer" % "3.0.1",
    "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
    "org.owasp.encoder" % "encoder" % "1.1",
    "org.jodd" % "jodd-wot" % "3.3.8" % "test",
    "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current % "test"
  )

  val dbEvolutionApplierDependencies = Seq(
    jdbc,
    evolutions,
    hikariCpDep,
    postgresqlDep
  )

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonProjectDependencies = Seq(
    akkaDep,
    asyncHttpClientDep,
    awsS3Dep,
    commonsIoDep,
    elasticSearchDep,
    geronimoJmsDep,
    guavaDep, // Textify
    hikariCpDep,
    logbackDep,
    pgSlickDep,
    playJsonDep,
    postgresqlDep,
    redisDep,
    slickDep,
    squerylDep,
    stompDep,
    specs2Dep % "test",
    junitInterfaceDep % "test",
    akkaTestkit % "test",
    mockitoDep % "test",
    junitDep % "test",
    // message-broker relies on scala-compiler, but a different version than
    // Squeryl does. That means two huge, useless jars in the distribution
    // instead of one. Require the latest version, so that the jars aren't
    // duplicated.
    "org.scala-lang" % "scala-compiler" % ourScalaVersion
  )

  val commonTestProjectDependencies = Seq(
    akkaDep,
    akkaTestkit,
    logbackDep,
    specs2Dep,
    junitInterfaceDep,
    junitDep,
    mockitoDep
  )

  val workerProjectDependencies = Seq(
    javaxMailDep, 
    openCsvDep,
    playStreamsDep
  )
  
  val documentSetWorkerProjectDependencies = Seq(
    akkaAgentDep,
    logbackDep,
    javaxMailDep,
    "org.overviewproject" % "mime-types" % "0.0.2",
    "org.apache.pdfbox" % "pdfbox" % "1.8.9",
    "org.bouncycastle" % "bcprov-jdk15" % "1.44",
    "org.bouncycastle" % "bcmail-jdk15" % "1.44"
  )
  
  val messageBrokerDependencies = Seq(
    // [adam] I tried excluding things while upgrading to Scala 2.11. It turns
    // out we need to stick with Scala 2.10 for this project. But I figured we
    // might as well leave the exclusions there.
    "org.apache.activemq" % "apache-apollo" % "1.7.1"
      exclude("org.apache.activemq", "apollo-openwire")
      exclude("org.apache.activemq", "apollo-amqp")
      exclude("org.apache.activemq", "apollo-mqtt")
      exclude("org.apache.activemq", "apollo-web"),
    javaxMailDep
  )

  val searchIndexDependencies = Seq(
    logbackDep,
    elasticSearchDep
  )

  val runnerDependencies = Seq(
    specs2Dep % "test",
    mockitoDep % "test",
    scalaArmDep,
    postgresqlDep,
    "org.rogach" %% "scallop" % "0.9.5"
  )
}
