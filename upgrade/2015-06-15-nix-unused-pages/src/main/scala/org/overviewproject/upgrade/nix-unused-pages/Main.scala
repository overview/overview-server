package org.overviewproject.upgrade.nix_useless_pages

import com.zaxxer.hikari.{HikariConfig,HikariDataSource}
import scala.concurrent.{Await,Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.{DataSource,Database,DatabaseConfiguration}
import org.overviewproject.models.tables.Pages

/** Nix Pages that nobody refers to.
  *
  * There used to be a lot of these. See
  * https://groups.google.com/d/msg/overview-dev/FJLJb3Kx1PY/eswrNfrbWHsJ
  */
object Main {
  private val BatchSize: Int = 1000
  private val BiggerBatchSize: Int = 500000 // Memory limit -- assume hundreds of bytes per row

  private val database: Database = {
    val databaseConfig: HikariConfig = DatabaseConfiguration.fromConfig
    val dataSource: HikariDataSource = new HikariDataSource(databaseConfig)
    new Database(dataSource)
  }
  import database.api._

  def main(args: Array[String]): Unit = deleteAllUnusedPages

  /** Calls deletePages a big batch at a time.
    *
    * This method is slow to start: it needs to find the list of all pages to
    * delete, which can take half an hour.
    */
  private def deleteAllUnusedPages: Unit = {
    while (deleteSomeUnusedPages > 0) {}

    System.out.println("There are no more pages to delete.")
  }

  /** Finds and deletes lots of Pages.
    *
    * Ideally this would be *all* pages, but we don't want to run out of memory
    * so we limit the number to BiggerBatchSize.
    *
    * This operation is slow, because it joins the Page table to the Document
    * table.
    */
  private def deleteSomeUnusedPages: Int = {
    System.out.println("Finding up to $s{BiggerBatchSize} pages to delete...")
    val pages: Seq[(Long,String)] = await(database.run(sql"""
      SELECT id, data_location
      FROM page
      WHERE NOT EXISTS (SELECT true FROM document WHERE id = page.id)
      LIMIT #$BiggerBatchSize
    """.as[(Long,String)]))

    pages.grouped(BatchSize).foreach(deletePages)
    pages.length
  }

  /** Deletes some Pages.
    *
    * The Long in the tuple is the page ID. The String is the S3 location.
    */
  private def deletePages(pages: Seq[(Long,String)]): Unit = {
    System.out.println(s"Deleting ${pages.length} pages...")
    val pageIds = pages.map(_._1)
    val locations = pages.map(_._2)

    await(BlobStorage.deleteMany(locations))
    await(database.delete(Pages.filter(_.id inSet pageIds)))
  }

  /** Makes things synchronous. Awesome. */
  private def await[A](future: Future[A]): A = {
    Await.result(future, Duration.Inf)
  }
}
