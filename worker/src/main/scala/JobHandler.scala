/**
 *
 * JobHandler.scala
 *
 * Overview,June 2012
 * @author Jonas Karlsson
 */

import java.util.TimeZone
import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import com.overviewdocs.database.{DB,DanglingNodeDeleter,HasBlockingDatabase}
import com.overviewdocs.models.tables.Trees
import com.overviewdocs.util.Logger

object JobHandler extends HasBlockingDatabase {
  import database.api._
  private val logger = Logger.forClass(getClass)

  private def await[T](future: Future[T]): T = blocking(Await.result(future, Duration.Inf))

  def main(args: Array[String]) {
    // Make sure java.sql.Timestamp values are correct
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    // Connect to the database
    DB.dataSource

    logger.info("Cleaning up dangling nodes")
    await(DanglingNodeDeleter.run)

    logger.info("Starting to scan for jobs")
    startHandlingJobs
  }

  private def startHandlingJobs: Unit = {
    val pollingInterval = 500 //milliseconds

    while (true) {
      handleTrees
      Thread.sleep(pollingInterval)
    }
  }

  // Build each tree currently listed in the database
  private def handleTrees: Unit = {
    blockingDatabase.seq(Trees.filter(_.progress =!= 1.0))
      .foreach { tree =>
        new com.overviewdocs.clustering.Runner(tree).runBlocking
      }
  }
}
