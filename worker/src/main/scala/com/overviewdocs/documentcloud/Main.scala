package com.overviewdocs.documentcloud

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.{HasDatabase,TreeIdGenerator}
import com.overviewdocs.models.{DocumentCloudImport,Tree}
import com.overviewdocs.models.tables.{DocumentCloudImports,DocumentProcessingErrors,Trees}
import com.overviewdocs.util.AddDocumentsCommon

trait Main extends HasDatabase {
  private val fetcher = new Fetcher

  /** Runs a DocumentCloudImport.
    *
    * 1. Fetches ID lists, one at a time, from DocumentCloud. Writes to
    *    `document_cloud_import_id_list`, updating 
    *    `document_cloud_import.n_id_lists_total` and `.n_id_lists_fetched`
    *    each time a list is added.
    * 2. Writes `document_cloud_import.n_total`.
    * 3. Fetches documents, many at a time, from DocumentCloud. Writes to
    *    `document_cloud_import.n_fetched` periodically.
    * 4. Deletes the `document_cloud_import` and
    *    `document_cloud_import_id_list`s.
    * 5. Creates a Tree.
    */
  def run(dcImport: DocumentCloudImport): Future[Unit] = {
    for {
      _ <- AddDocumentsCommon.beforeAddDocuments(dcImport.documentSetId)
      result <- new IdListFetcher(dcImport).run
      dcImport2 <- updateDocumentCloudImport(dcImport, result)
      _ <- fetchDocuments(dcImport2)
      _ <- AddDocumentsCommon.afterAddDocuments(dcImport.documentSetId)
      _ <- cleanUp(dcImport.id)
      _ <- createTree(dcImport)
    } yield ()
  }

  private lazy val errorInserter = {
    import database.api._
    DocumentProcessingErrors.map(dpe => (dpe.documentSetId, dpe.textUrl, dpe.message))
  }

  private lazy val updateNTotalCompiled = {
    import database.api._
    Compiled { dcImportId: Rep[Int] =>
      DocumentCloudImports.filter(_.id === dcImportId).map(_.nTotal)
    }
  }

  private def writeError(documentSetId: Long, query: String, message: String): Future[Unit] = {
    import database.api._
    database.runUnit(errorInserter.+=((documentSetId, query, message)))
  }

  private def writeTotal(dcImportId: Int, nTotal: Int): Future[Unit] = {
    import database.api._
    database.runUnit(updateNTotalCompiled(dcImportId).update(Some(nTotal)))
  }

  private def updateDocumentCloudImport(
    dcImport: DocumentCloudImport,
    result: IdListFetcher.Result
  ): Future[DocumentCloudImport] = result match {
    case IdListFetcher.Stop(message) => {
      for {
        _ <- writeError(dcImport.documentSetId, dcImport.query, message)
      } yield dcImport.copy(cancelled=true)
    }
    case IdListFetcher.Success(nLists, nDocuments, nPages) => {
      val nTotal = if (dcImport.splitPages) nPages else nDocuments
      for {
        _ <- writeTotal(dcImport.id, nTotal)
      } yield dcImport.copy(nIdListsTotal=Some(nLists), nTotal=Some(nTotal))
    }
  }

  private def updateProgressAndCheckContinue(dcImportId: Int, nFetched: Int): Future[Boolean] = {
    import database.api._

    database.option(sql"""
      UPDATE document_cloud_import
      SET n_fetched = $nFetched
      WHERE id = $dcImportId
      RETURNING cancelled
    """.as[Boolean]).map(_ == Some(false))
  }

  private def fetchDocuments(dcImport: DocumentCloudImport): Future[Unit] = {
    if (dcImport.cancelled) return Future.successful(())

    val headerProducer = new HeaderProducer(dcImport)

    def updateProgressAndMaybeCancel(nFetched: Int): Future[Unit] = {
      for {
        continue <- updateProgressAndCheckContinue(dcImport.id, nFetched)
      } yield {
        if (!continue) headerProducer.stop
      }
    }

    val writer = new DocumentWriter(dcImport, updateProgressAndMaybeCancel)
    writer.flushPeriodically

    val doneFetching = Future.sequence(Seq(
      fetcher.run(headerProducer, writer),
      fetcher.run(headerProducer, writer),
      fetcher.run(headerProducer, writer),
      fetcher.run(headerProducer, writer)
    ))

    for {
      _ <- doneFetching
      _ <- writer.stop
    } yield ()
  }

  private def cleanUp(dcImportId: Int): Future[Unit] = {
    import database.api._

    database.runUnit(sqlu"""
      WITH delete1 AS (
        DELETE FROM document_cloud_import_id_list
        WHERE document_cloud_import_id = $dcImportId
      )
      DELETE FROM document_cloud_import WHERE id = $dcImportId
    """)
  }

  def createTree(dcImport: DocumentCloudImport): Future[Unit] = {
    import database.api._

    for {
      treeId <- TreeIdGenerator.next(dcImport.documentSetId)
      _ <- database.runUnit(Trees.+=(Tree.CreateAttributes(
        documentSetId=dcImport.documentSetId,
        lang=dcImport.lang
      ).toTreeWithId(treeId)))
    } yield ()
  }
}

object Main extends Main
