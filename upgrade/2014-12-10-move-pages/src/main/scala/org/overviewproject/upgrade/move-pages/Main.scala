package org.overviewproject.upgrade.move_pages

import java.io.ByteArrayInputStream
import play.api.libs.iteratee.Iteratee
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import org.overviewproject.blobstorage.{BlobBucketId,BlobStorage}
import org.overviewproject.database.{DB,DataSource,DeprecatedDatabase,DatabaseConfiguration}
import org.overviewproject.models.tables.Pages

/** Move page.data to wherever it is configured to go in application.conf.
  *
  * (In other words, use application.conf and environment variables.)
  *
  * We move one file at a time, to keep things simple. Do <em>not</em> run
  * multiple instances of this program, or the two versions will interfere
  * and catastrophe will ensue.
  *
  * We assume nobody is adding to page.data while we process. The whole point
  * of this script is to get rid of it, after all.
  */
object Main {
  private val BatchSize: Int = 1000

  def main(args: Array[String]): Unit = {
    connectToDatabase
    moveAllPages
  }

  /** Globally connects to the database using settings in config. */
  def connectToDatabase: Unit = {
    val databaseConfig = DatabaseConfiguration.fromConfig
    val dataSource = DataSource(databaseConfig)
    DB.connect(dataSource)
  }

  /** Calls moveSomePages() a batch at a time. */
  private def moveAllPages: Unit = {
    while (BatchSize == moveSomePages(BatchSize)) {
      // After a full batch, there may be more
      System.out.println("Yay, another batch of pages!")
    }

    // After a half-batch (or 0), there are no more
    System.out.println("There are no more pages to move.")
  }

  /** Finds some pages that need moving and moves them one by one. */
  private def moveSomePages(limit: Int): Int = {
    val pageIds = findSomePageIds(limit)
    System.out.println(s"Working on a batch of ${pageIds.length} pages...")
    pageIds.foreach(movePage _)
    pageIds.length
  }

  /** Copies data to BlobStorage and then removes it from the database.
    *
    * We read the entire file into memory and then write it from memory. Why?
    * Because it's simple!
    */
  private def movePage(pageId: Long): Unit = {
    val data: Array[Byte] = readPage(pageId)
    val location: String = writePageData(pageId, data)
    updatePage(pageId, location)
  }

  /** Reads a page into memory. */
  private def readPage(pageId: Long): Array[Byte] = {
    System.out.print(s"Page ${pageId}: opening...")
    System.out.flush
    val enumerator = await(BlobStorage.get("pagebytea:" + pageId))

    System.out.print(" reading...")
    System.out.flush
    val ret = await(enumerator.run(Iteratee.consume()))

    System.out.println(s" read ${ret.length} bytes")
    ret
  }

  /** Writes a page into BlobStorage and returns its location. */
  private def writePageData(pageId: Long, data: Array[Byte]): String = {
    System.out.print(s"Page ${pageId}: opening...")
    System.out.flush
    val future = BlobStorage.create(BlobBucketId.PageData, new ByteArrayInputStream(data), data.length)

    System.out.print(s" writing ${data.length} bytes...")
    System.out.flush
    val ret = await(future)

    System.out.println(s" done. Location: ${ret}")
    ret
  }

  /** Updates a page in the database to have the new location and NULL data. */
  private def updatePage(pageId: Long, location: String): Unit = {
    System.out.println(s"Updating page ${pageId} to have location ${location} and data NULL...")

    DeprecatedDatabase.withSlickSession { session =>
      import org.overviewproject.database.Slick.simple._
      Pages
        .filter(_.id === pageId)
        .map((p) => (p.dataLocation, p.data))
        .update((Some(location), None))(session)
    }
  }

  /** Finds some page IDs that need copying. */
  private def findSomePageIds(limit: Int): Seq[Long] = {
    System.out.println(s"Finding <= ${limit} pages to transfer...")

    DeprecatedDatabase.withSlickSession { session =>
      import org.overviewproject.database.Slick.simple._
      Pages
        .filter(_.data.isDefined)
        .map(_.id)
        .take(1000)
        .list(session)
    }
  }

  /** Makes things synchronous. Awesome. */
  private def await[A](future: Future[A]): A = {
    Await.result(future, Duration.Inf)
  }
}
