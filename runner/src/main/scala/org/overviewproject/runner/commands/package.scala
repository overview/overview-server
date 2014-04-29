package org.overviewproject.runner

import java.io.File
import scala.io.Source

package object commands {
  trait UsefulCommands {
    def documentSetWorker: Command
    def messageBroker: Command
    def searchIndex: Command
    def webServer: Command
    def worker: Command
    def runEvolutions: Command

    /** Runs sbt. If not present, throws a run-time exception. */
    def sbt(task: String): Command = ???

    def sh(task: String): Command = new Command(Seq(), task.split(' '))
  }

  object development extends UsefulCommands {
    private lazy val sbtLaunchJar = getClass.getResource("/sbt-launch.jar")
    private lazy val sbtLaunchPath = new File(sbtLaunchJar.toURI()).getAbsolutePath()

    override def searchIndex = new JvmCommandWithAppendableClasspath(
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

    override def messageBroker = new JvmCommandWithAppendableClasspath(
      Seq(),
      Seq(Flags.ApolloBase),
      Seq(
        "org.apache.activemq.apollo.boot.Apollo",
        "documentset-worker/lib",
        "org.apache.activemq.apollo.cli.Apollo",
        "run"
      )
    )

    override def webServer = new JvmCommand(
      // We run "overview-server/run" through sbt. That lets it reload files
      // as they're edited.
      Seq(),
      Seq(
        "-XX:MaxPermSize=512M",
        "-Duser.timezone=UTC",
        "-Dpidfile.enabled=false"
      ),
      Seq(
        "-jar", sbtLaunchPath,
        "run"
      )
    )

    override def documentSetWorker = new JvmCommandWithAppendableClasspath(
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml"),
      Seq("org.overviewproject.DocumentSetWorker")
    )

    override def worker = new JvmCommandWithAppendableClasspath(
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml", "-Xmx2g"),
      Seq("JobHandler")
    ).with32BitSafe

    override def runEvolutions = new JvmCommand(
      Seq(),
      Seq(
        Flags.DatabaseUrl,
        "-Dsbt.log.format=false"
      ),
      Seq(
        "-jar", sbtLaunchPath,
        "db-evolution-applier/run"
      )
    )

    /** A Command for "sbt [task]" */
    override def sbt(task: String) = new JvmCommand(
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
    ).with32BitSafe
  }

  object production extends UsefulCommands {
    private def cmd(prefix: String, jvmArgs: Seq[String], args: Seq[String]) = {
      val classPath = Source
        .fromFile(s"${prefix}/classpath.txt")
        .getLines
        .map((s) => s"lib/${s}")
        .mkString(File.pathSeparator)

      val fullJvmArgs = jvmArgs ++ Seq("-cp", classPath)
      new JvmCommand(Seq(), fullJvmArgs, args)
    }

    override def documentSetWorker = cmd(
      "documentset-worker",
      Seq(
        Flags.DatabaseUrl,
        Flags.DatabaseDriver,
        "-Dlogback.configurationFile=workerdevlog.xml"
      ),
      Seq("org.overviewproject.DocumentSetWorker")
    )

    override def messageBroker = cmd(
      "message-broker",
      Seq(Flags.ApolloBase),
      Seq(
        "org.apache.activemq.apollo.boot.Apollo",
        "documentset-worker/lib",
        "org.apache.activemq.apollo.cli.Apollo",
        "run"
      )
    )

    override def searchIndex = cmd(
      "search-index",
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
        "-Des.path.home=./search-index",
        // Put the search index in the same directory as Postgres. That way
        // all user data gets stored in one directory. (Yes, it's a bit of a
        // hack.)
        "-Des.path.data=database/search-index"
      ),
      Seq("org.elasticsearch.bootstrap.ElasticSearch")
    )

    override def webServer = {
      val cmdLineFlags: Seq[String] = sys.props
        .filterKeys(_.startsWith("overview."))
        .toSeq
        .map(Function.tupled((k: String, v: String) => s"-D${k}=${v}"))

      cmd(
        // In distribution mode, the web server is in the "frontend/" folder
        "frontend",
        cmdLineFlags ++ Seq(
          Flags.DatabaseUrl,
          "-Duser.timezone=UTC",
          "-Dpidfile.enabled=false"
        ),
        Seq(
          "play.core.server.NettyServer"
        )
      )
    }

    override def worker = cmd(
      "worker",
      Seq(
        Flags.DatabaseUrl,
        Flags.DatabaseDriver,
        "-Dlogback.configurationFile=workerdevlog.xml",
        "-Xmx2g"
      ),
      Seq("JobHandler")
    ).with32BitSafe

    override def runEvolutions = cmd(
      "db-evolution-applier",
      Seq(Flags.DatabaseUrl),
      Seq("org.overviewproject.db_evolution_applier.Main")
    )
  }
}
