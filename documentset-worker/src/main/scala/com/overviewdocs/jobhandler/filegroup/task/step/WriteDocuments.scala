package com.overviewdocs.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.DocumentIdSupplierProtocol._
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.TempDocumentSetFiles
import com.overviewdocs.util.BulkDocumentWriter

/** Writes [[Document]]s to the database and deletes [[TempDocumentSetFile]]s.
  */
class WriteDocuments(
  override protected val documentSetId: Long,
  override protected val filename: String,
  val documentsWithoutIds: Seq[DocumentWithoutIds],
  val documentIdSupplier: ActorRef,
  val bulkDocumentWriter: BulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex
)(implicit protected override val executor: ExecutionContext)
extends UploadedFileProcessStep with HasDatabase {
  private def makeDocument(documentWithoutIds: DocumentWithoutIds, id: Long): Document = {
    documentWithoutIds.toDocument(documentSetId, id)
  }

  override protected def doExecute: Future[TaskStep] = for {
    IdRequestResponse(ids) <- documentIdSupplier.ask(RequestIds(documentSetId, documentsWithoutIds.size))(Timeout(30, TimeUnit.SECONDS))
    _ <- writeDocuments(documentsWithoutIds.zip(ids).map((makeDocument _).tupled))
    _ <- deleteTempDocumentSetFiles
  } yield FinalStep

  private def writeDocuments(documents: Seq[Document]): Future[Unit] = {
    val it = documents.iterator

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
