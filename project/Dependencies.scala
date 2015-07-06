import sbt._
import play.sbt.PlayImport.{evolutions,filters,jdbc,ws}

object Dependencies {
  private object deps {
    // shared dependencies
    val akkaAgent = "com.typesafe.akka" %% "akka-agent" % "2.3.4"
    val akka = "com.typesafe.akka" %% "akka-actor" % "2.3.4"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"  % "2.3.4"
    val asyncHttpClient = "com.ning" % "async-http-client" % "1.7.18"
    val awsCore = "com.amazonaws" % "aws-java-sdk-core" % "1.9.23"
    val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % "1.9.23"
    val bcmail = "org.bouncycastle" % "bcmail-jdk15" % "1.44" // https://pdfbox.apache.org/1.8/dependencies.html
    val bcprov = "org.bouncycastle" % "bcprov-jdk15" % "1.44" // https://pdfbox.apache.org/1.8/dependencies.html
    val bcrypt = "com.github.t3hnar" %% "scala-bcrypt" % "2.4"
    val commonsIo = "commons-io" % "commons-io" % "2.4"
    val elasticSearch = "org.elasticsearch" % "elasticsearch" % "1.4.2"
    val geronimoJms = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.0" // javax.jms
    val guava = "com.google.guava" % "guava" % "18.0"
    val hikariCp = "com.zaxxer" % "HikariCP" % "2.3.8"
    val janino = "janino" % "janino" % "2.5.10" // Runtime Java compiler, for Logback -- see logback-test.xml
    val javaxMail = "javax.mail" % "mail" % "1.4.7"
    val joddWot = "org.jodd" % "jodd-wot" % "3.3.8"
    val junitInterface = "com.novocode" % "junit-interface" % "0.9"
    val junit = "junit" % "junit-dep" % "4.11"
    val logback = "ch.qos.logback" % "logback-classic" % "1.0.9"
    val mimeTypes = "org.overviewproject" % "mime-types" % "0.0.2"
    val mockito = "org.mockito" % "mockito-all" % "1.9.5"
    val openCsv = "com.opencsv" % "opencsv" % "3.4"
    val owaspEncoder = "org.owasp.encoder" % "encoder" % "1.1"
    val pdfbox = "org.apache.pdfbox" % "pdfbox" % "1.8.9"
    val pgSlick = "com.github.tminglei" %% "slick-pg" % "0.9.0"
    val playJson = "com.typesafe.play" %% "play-json" % play.core.PlayVersion.current
    val playMailer = "com.typesafe.play" %% "play-mailer" % "3.0.1"
    val playPluginsUtil = "com.typesafe.play.plugins" %% "play-plugins-util" % "2.3.0"
    val playStreams = "com.typesafe.play" %% "play-streams-experimental" % play.core.PlayVersion.current
    val playTest = "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current
    val postgresql = "org.postgresql" % "postgresql" % "9.3-1103-jdbc41"
    val redis = "net.debasishg" %% "redisreact" % "0.6"
    val scalaArm = "com.jsuereth" %% "scala-arm" % "1.4"
    val scallop = "org.rogach" %% "scallop" % "0.9.5"
    val slick = "com.typesafe.slick" %% "slick" % "3.0.0"
    val specs2 = "org.specs2" %% "specs2" % "2.3.13"
    val squeryl = "org.squeryl" %% "squeryl" % "0.9.6-RC3"
    val stomp = "org.fusesource.stompjms" % "stompjms-client" % "1.15"

    // Hacky deps below

    /*
     * Make Squeryl's scala-compiler go away by requiring our own version.
     *
     * TODO: nix Squeryl, then nix this dependency.
     */
    val hackyScalaCompiler = "org.scala-lang" % "scala-compiler" % "2.11.6"
  }

  val serverDependencies = Seq(
    deps.bcrypt,
    deps.openCsv,
    deps.owaspEncoder,
    deps.playMailer,
    deps.playPluginsUtil,
    deps.playStreams,
    deps.slick,
    filters,
    ws,
    deps.joddWot % "test",
    deps.playTest % "test"
  )

  val dbEvolutionApplierDependencies = Seq(
    deps.hikariCp,
    deps.postgresql,
    evolutions,
    jdbc
  )

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonDependencies = Seq(
    deps.akka,
    deps.asyncHttpClient,
    deps.awsS3,
    deps.commonsIo,
    deps.elasticSearch,
    deps.geronimoJms,
    deps.guava, // Textify
    deps.hackyScalaCompiler, // boo
    deps.hikariCp,
    deps.logback,
    deps.pgSlick,
    deps.playJson,
    deps.postgresql,
    deps.redis,
    deps.slick,
    deps.squeryl, // boo
    deps.stomp, // boo
    deps.akkaTestkit % "test",
    deps.janino % "test", // See logback-text.xml
    deps.junitInterface % "test",
    deps.junit % "test",
    deps.mockito % "test",
    deps.specs2 % "test"
  )

  val commonTestDependencies = Seq(
    deps.akka,
    deps.akkaTestkit,
    deps.junit,
    deps.junitInterface,
    deps.logback,
    deps.mockito,
    deps.specs2
  )

  val workerDependencies = Seq(
    deps.javaxMail,
    deps.openCsv,
    deps.playStreams
  )
  
  val documentSetWorkerDependencies = Seq(
    deps.akkaAgent,
    deps.bcmail,
    deps.bcprov,
    deps.javaxMail,
    deps.logback,
    deps.mimeTypes,
    deps.pdfbox
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
    deps.javaxMail
  )

  val searchIndexDependencies = Seq(
    deps.elasticSearch
  )

  val runnerDependencies = Seq(
    deps.postgresql,
    deps.scalaArm,
    deps.scallop,
    deps.mockito % "test",
    deps.specs2 % "test"
  )
}
