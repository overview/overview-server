package org.overviewproject.runner

import java.io.File

package object commands {
  private val sbtLaunchUri = getClass.getResource("/sbt-launch.jar").toURI()
  private val sbtLaunchPath = new File(sbtLaunchUri).getAbsolutePath()

  val SearchIndex: Command = new JvmCommandWithAppendableClasspath(
    Seq(),
    Seq(
      "-Xmx1g",
      "-Xss256k",
      "-XX:+UseParNewGC",
      "-XX:+UseConcMarkSweepGC",
      "-XX:CMSInitiatingOccupancyFraction=75",
      "-XX:+UseCMSInitiatingOccupancyOnly",
      "-Djava.awt.headless=true",
      "-Delasticsearch",
      "-Des.foreground=yes",
      "-Des.path.home=./search-index"
    ),
    Seq("org.elasticsearch.bootstrap.ElasticSearch")
  )

  val MessageBroker: Command = new JvmCommandWithAppendableClasspath(
    Seq(),
    Seq(Flags.ApolloBase),
    Seq(
      "org.apache.activemq.apollo.boot.Apollo",
      "documentset-worker/lib",
      "org.apache.activemq.apollo.cli.Apollo",
      "run"
    )
  )

  val OverviewServer: Command = new JvmCommand(
    // We run "overview-server/run" through sbt. That lets it reload files
    // as they're edited.
    Seq(),
    Seq(
      "-XX:MaxPermSize=512M",
      "-Dpidfile.enabled=false",
      "-DapplyEvolutions.default=true" // So overview-worker works on first launch
    ),
    Seq(
      "-jar", sbtLaunchPath,
      "run"
    )
  )

  val DocumentSetWorker: Command = new JvmCommandWithAppendableClasspath(
    Seq(),
    Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml"),
    Seq("org.overviewproject.DocumentSetWorker")
  )

  val Worker: Command = new JvmCommandWithAppendableClasspath(
    Seq(),
    Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml", "-Xmx2g"),
    Seq("JobHandler")
  )

  /** A Command for "sbt [task]" */
  def sbt(task: String) : Command = new JvmCommand(
    Seq(),
    Seq(
      "-Dsbt.log.format=false",
      "-XX:MaxPermSize=512M",
      "-Xmx2g"
    ),
    Seq(
      "-jar", sbtLaunchPath,
      task
    )
  )

  val PostgresServerCommand: Command = {
    val dataDir = new File("database").getAbsolutePath()
    PostgresCommand(
      "postgres",
      "-D", dataDir,
      "-k", dataDir
    )
  }
}
