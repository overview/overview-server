package com.overviewdocs.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.DocumentIdSupplierProtocol.{RequestIds,IdRequestResponse}
import com.overviewdocs.jobhandler.filegroup.task.FilePipelineParameters
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.TempDocumentSetFiles
import com.overviewdocs.util.BulkDocumentWriter

/** Writes [[Document]]s to the database and deletes [[TempDocumentSetFile]]s.
  */
class WriteDocuments(
  val documentsWithoutIds: Seq[DocumentWithoutIds],
  val params: FilePipelineParameters
)(implicit ec: ExecutionContext)
extends HasDatabase {
  def execute: Future[Unit] = for {
    ids <- getIds
    _ <- writeDocuments(documentsWithoutIds.zip(ids).map((makeDocument _).tupled))
    _ <- deleteTempDocumentSetFiles
  } yield ()

  private def makeDocument(documentWithoutIds: DocumentWithoutIds, id: Long): Document = {
    documentWithoutIds.toDocument(params.documentSetId, id)
  }

  private def getIds: Future[Seq[Long]] = {
    val request = RequestIds(params.documentSetId, documentsWithoutIds.size)
    val timeout = Timeout(9999999, TimeUnit.SECONDS)

    for {
      IdRequestResponse(ids) <- params.documentIdSupplier.ask(request)(timeout)
    } yield ids
  }

  private def writeDocuments(documents: Seq[Document]): Future[Unit] = {
    val it = documents.iterator
    val bulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex

    def step: Future[Unit] = it.hasNext match {
      case true => bulkDocumentWriter.addAndFlushIfNeeded(it.next).flatMap(_ => step)
      case false => bulkDocumentWriter.flush
    }

    step
  }

  private def deleteTempDocumentSetFiles: Future[Unit] = {
    import database.api._

    val fileIds = documentsWithoutIds.map(_.fileId).flatten.toSet
    database.runUnit(TempDocumentSetFiles.filter(_.fileId inSet fileIds).delete)
  }
}

object WriteDocuments {
  def apply(
    documentsWithoutIds: Seq[DocumentWithoutIds],
    params: FilePipelineParameters
  )(implicit ec: ExecutionContext): Future[Unit] = {
    new WriteDocuments(documentsWithoutIds, params).execute
  }
}
