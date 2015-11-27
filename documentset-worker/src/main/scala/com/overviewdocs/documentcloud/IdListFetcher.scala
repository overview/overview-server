package com.overviewdocs.documentcloud

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentCloudImport,DocumentCloudImportIdList}
import com.overviewdocs.models.tables.{DocumentCloudImports,DocumentCloudImportIdLists}

/** Writes DocumentCloudIdLists to the database and returns the number of them
  * once they are all written.
  */
class IdListFetcher(dcImport: DocumentCloudImport, server: DocumentCloudServer)
extends HasDatabase {
  import database.api._

  sealed trait Result

  /** We finished gathering IdLists. */
  case class Success(nLists: Int, nDocuments: Int, nPages: Int) extends Result

  /** Either the user cancelled or we got an HTTP error.
    *
    * We stopped processing. The caller should write this message somewhere and
    * delete the entire import.
    */
  case class Stop(message: String) extends Result

  private val CancelledMessage = "This import was cancelled"

  private var error: Option[String] = if (dcImport.cancelled) {
    Some(CancelledMessage)
  } else {
    None
  }

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
      server.getIdList0(dcImport.query, dcImport.username, dcImport.password).map(_ match {
        case Right((idList, nDocumentsTotal)) => {
          nTotal = Some(nDocumentsTotal / 1000) // TODO put page size somewhere
          Right(idList)
        }
        case Left(error) => Left(error)
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
    nDocuments += nDocuments
    nPages += nPages

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
          _ <- reportProgressAndCheckContinue
        } yield ()
      }
    })
  }

  private def continue: Future[Unit] = {
    if (error.nonEmpty || Some(nFetched) == nTotal) {
      Future.successful(())
    } else {
      step.flatMap(_ => continue)
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

  private def run: Future[Result] = {
    for {
      _ <- loadStateFromDatabase
      _ <- continue
    } yield result
  }

  private def result: Result = error match {
    case Some(message) => Stop(message)
    case None => Success(nFetched, nDocuments, nPages)
  }
}
