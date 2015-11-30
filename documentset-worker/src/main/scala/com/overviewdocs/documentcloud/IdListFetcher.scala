package com.overviewdocs.documentcloud

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentCloudImport,DocumentCloudImportIdList}
import com.overviewdocs.models.tables.{DocumentCloudImports,DocumentCloudImportIdLists}

/** Writes DocumentCloudIdLists to the database and returns the number of them
  * once they are all written.
  */
class IdListFetcher(
  dcImport: DocumentCloudImport,
  server: DocumentCloudServer = DocumentCloudServer,
  val listSize: Int = 1000
)
extends HasDatabase {
  import database.api._

  assert(listSize > 0)

  private val CancelledMessage = "This import was cancelled"

  private var error: Option[String] = None // we always check in DB
  private var nTotal: Option[Int] = dcImport.nIdListsTotal
  private var nFetched: Int = 0   // set in resume()
  private var nDocuments: Int = 0 // set in resume()
  private var nPages: Int = 0     // set in resume()

  private lazy val inserter = DocumentCloudImportIdLists.map(_.createAttributes)

  private def reportProgressAndCheckContinue: Future[Unit] = {
    database.option(sql"""
      UPDATE document_cloud_import
      SET n_id_lists_fetched = $nFetched, n_id_lists_total = $nTotal
      WHERE id = ${dcImport.id}
      RETURNING cancelled
    """.as[Boolean]).map(_ match {
      case Some(false) => {}
      case _ => error = Some(CancelledMessage)
    })
  }

  /** Fetches the next IdList.
    *
    * As a side-effect, updates `nTotal` on first fetch.
    */
  private def fetchNextIdListAndUpdateNTotal: Future[Either[String,IdList]] = {
    if (nTotal.isEmpty) {
      server.getIdList0(dcImport.query, dcImport.username, dcImport.password).flatMap(_ match {
        case Right((idList, nDocumentsTotal)) => {
          nTotal = Some(math.max(1, (nDocumentsTotal + listSize - 1) / listSize))
          // Update nTotal in the database *before* writing any ID lists.
          // Otherwise, if you try to resume but nTotal isn't set, you'll
          // try to re-insert page 0 (which will crash).
          reportProgressAndCheckContinue.map(_ => Right(idList))
        }
        case Left(error) => Future.successful(Left(error))
      })
    } else {
      server.getIdList(dcImport.query, dcImport.username, dcImport.password, nFetched)
    }
  }

  /** Writes the IdList to the database and updates counters.
    */
  private def writeIdListAndUpdateState(idList: IdList): Future[Unit] = {
    val attributes = DocumentCloudImportIdList.CreateAttributes(
      dcImport.id,
      nFetched,
      IdList.encode(idList),
      idList.nDocuments,
      idList.nPages
    )

    nFetched += 1
    nDocuments += idList.nDocuments
    nPages += idList.nPages

    database.runUnit(inserter.+=(attributes))
  }

  /** Fetch one IdList, write it to the database, and update progress.
    */
  private def step: Future[Unit] = {
    fetchNextIdListAndUpdateNTotal.flatMap(_ match {
      case Left(message) => {
        error = Some(message)
        Future.successful(())
      }
      case Right(idList) => {
        for {
          _ <- writeIdListAndUpdateState(idList)
        } yield ()
      }
    })
  }

  private def continue: Future[Unit] = {
    reportProgressAndCheckContinue.flatMap { _ =>
      if (error.nonEmpty || Some(nFetched) == nTotal) {
        Future.successful(())
      } else {
        step.flatMap(_ => continue)
      }
    }
  }

  /** Fetches previous counts from the database.
    */
  private def loadStateFromDatabase: Future[Unit] = {
    // Aggregate queries in Slick are more trouble than they're worth
    for {
      maybeTuple <- database.option(sql"""
        SELECT
          COUNT(*),
          SUM(n_documents),
          SUM(n_pages)
        FROM document_cloud_import_id_list
        WHERE document_cloud_import_id = ${dcImport.id}
      """.as[(Int,Option[Int],Option[Int])])
    } yield {
      val tuple = maybeTuple.get // assume Postgres is an RDBMS
      nFetched = tuple._1
      nDocuments = tuple._2.getOrElse(0)
      nPages = tuple._3.getOrElse(0)
    }
  }

  private def result: IdListFetcher.Result = error match {
    case Some(message) => IdListFetcher.Stop(message)
    case None => IdListFetcher.Success(nFetched, nDocuments, nPages)
  }

  def run: Future[IdListFetcher.Result] = {
    for {
      _ <- loadStateFromDatabase
      _ <- continue
    } yield result
  }
}

object IdListFetcher {
  sealed trait Result

  /** We finished gathering IdLists. */
  case class Success(nLists: Int, nDocuments: Int, nPages: Int) extends Result

  /** Either the user cancelled or we got an HTTP error.
    *
    * We stopped processing. The caller should write this message somewhere and
    * delete the entire import.
    */
  case class Stop(message: String) extends Result
}
