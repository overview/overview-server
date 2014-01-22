package org.overviewproject.db_evolution_applier

import com.typesafe.config.ConfigFactory
import java.io.File
import play.api.{Configuration,DefaultApplication,Mode,Plugin,Play}

/** Simple program to run Play evolutions.
  *
  * This can't be a simple sbt task, since it has Play's dependencies.
  *
  * When this task ends with status code 0, that means the database evolutions
  * have all been applied.
  */
object Main {
  def main(args: Array[String]) : Unit = {
    val config = ConfigFactory.parseFile(new File("./conf/application.conf")).resolve()
    val extraConfig = ConfigFactory.parseString("""
      |applyEvolutions.default=true
      |db.default.partitionCount=1
      |db.default.maxConnectionsPerPartition=2 # not 1. rrgh, Play. rrgh.
      |db.default.minConnectionsPerPartition=1
      |""".stripMargin).resolve()

    val application = new DefaultApplication(
      new java.io.File("."),
      getClass.getClassLoader,
      None,
      Mode.Prod
    ) {
      override lazy val configuration = Configuration(config) ++ Configuration(extraConfig)

      override lazy val plugins : Seq[play.api.Plugin] = Seq(
        new play.api.db.BoneCPPlugin(this),
        new play.api.db.evolutions.EvolutionsPlugin(this)
      )
    }

    Play.start(application)
    Play.stop()
  }
}
