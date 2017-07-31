import sbt._
import play.sbt.PlayImport.{guice,filters,ws}

object Dependencies {
  private object deps {
    // shared dependencies
    val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.3"
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % "2.5.3"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"  % "2.5.3"
    val asyncHttpClient = "com.ning" % "async-http-client" % "1.9.31"
    val awsCore = "com.amazonaws" % "aws-java-sdk-core" % "1.10.32"
    val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % "1.10.32"
    val bcrypt = "com.github.t3hnar" %% "scala-bcrypt" % "3.1"
    val config = "com.typesafe" % "config" % "1.3.0"
    val flywayDb = "org.flywaydb" % "flyway-core" % "3.2.1"
    val guava = "com.google.guava" % "guava" % "18.0"
    val icu4j = "com.ibm.icu" % "icu4j" % "56.1"
    val janino = "org.codehaus.janino" % "janino" % "2.7.8" // Runtime Java compiler -- for logback-test.xml
    val joddWot = "org.jodd" % "jodd-wot" % "3.3.8"
    val junitInterface = "com.novocode" % "junit-interface" % "0.9"
    val junit = "junit" % "junit-dep" % "4.11"
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
    val lucene = "org.apache.lucene" % "lucene-core" % "6.5.1"
    val luceneAnalyzersIcu = "org.apache.lucene" % "lucene-analyzers-icu" % "6.5.1"
    val luceneHighlighter = "org.apache.lucene" % "lucene-highlighter" % "6.5.1"
    val mimeTypes = "org.overviewproject" % "mime-types" % "0.0.2"
    val mockito = "org.mockito" % "mockito-all" % "1.9.5"
    val owaspEncoder = "org.owasp.encoder" % "encoder" % "1.1"
    val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6" // QueryParser
    val pdfocr = "org.overviewproject" %% "pdfocr" % "0.0.5"
    val pgSlick = "com.github.tminglei" %% "slick-pg" % "0.15.1"
    val playIteratees = "com.typesafe.play" %% "play-iteratees" % "2.6.1" // DELETEME
    val playJson = "com.typesafe.play" %% "play-json" % "2.6.2"
    val playMailer = "com.typesafe.play" %% "play-mailer" % "6.0.0"
    val playMailerGuice = "com.typesafe.play" %% "play-mailer-guice" % "6.0.0"
    val playTest = "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current
    val postgresql = "org.postgresql" % "postgresql" % "42.1.1"
    val redis = "com.github.etaty" %% "rediscala" % "1.8.0"
    val slick = "com.typesafe.slick" %% "slick" % "3.2.0"
    val slickHikariCp = "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0"
    val specs2Core = "org.specs2" %% "specs2-core" % "3.8.9"
    val specs2Junit = "org.specs2" %% "specs2-junit" % "3.8.9" // for XML reporting, for Jenkins
    val specs2MatcherExtra = "org.specs2" %% "specs2-matcher-extra" % "3.8.9"
    val specs2Mock = "org.specs2" %% "specs2-mock" % "3.8.9"
  }

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonDependencies = Seq(
    deps.akka,
    deps.akkaRemote,
    deps.awsS3,
    deps.guava, // Textify
    deps.icu4j, // Document.tokens
    deps.logback,
    deps.parserCombinators,
    deps.pgSlick,
    deps.playJson,
    deps.postgresql,
    deps.slick,
    deps.slickHikariCp,
    deps.akkaTestkit % "test",
    deps.janino % "test", // See logback-test.xml
    deps.junitInterface % "test",
    deps.junit % "test",
    deps.mockito % "test",
    deps.specs2Core % "test",
    deps.specs2Junit % "test",
    deps.specs2MatcherExtra % "test",
    deps.specs2Mock % "test"
  )

  val dbEvolutionApplierDependencies = Seq(
    deps.config,
    deps.flywayDb,
    deps.logback,
    deps.postgresql
  )

  val serverDependencies = Seq(
    deps.bcrypt,
    deps.owaspEncoder,
    deps.playIteratees, // DELETEME
    deps.playMailer,
    deps.playMailerGuice,
    deps.redis,
    filters,
    guice,
    ws,
    deps.joddWot % "test",
    deps.playTest % "test"
  )
  
  val workerDependencies = Seq(
    deps.asyncHttpClient,
    deps.logback,
    deps.lucene,
    deps.luceneAnalyzersIcu,
    deps.luceneHighlighter,
    deps.mimeTypes,
    deps.pdfocr,
    deps.janino % "test" // See logback-test.xml
  )
}
