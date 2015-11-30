package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.DocumentIdSupplierProtocol.{RequestIds,IdRequestResponse}
import com.overviewdocs.models.Document
import com.overviewdocs.util.BulkDocumentWriter

/** Writes [[Document]]s to the database. */
class WriteDocuments(
  val documentSetId: Long,
  val documentsWithoutIds: Seq[IncompleteDocument],
  val metadataJson: JsObject,
  val documentIdSupplier: ActorRef
)(implicit ec: ExecutionContext)
extends HasDatabase {
  def execute: Future[Unit] = for {
    ids <- getIds
    _ <- writeDocuments(documentsWithoutIds.zip(ids).map((makeDocument _).tupled))
  } yield ()

  private def makeDocument(documentWithoutIds: IncompleteDocument, id: Long): Document = {
    documentWithoutIds.toDocument(documentSetId, id, metadataJson)
  }

  private def getIds: Future[Seq[Long]] = {
    val request = RequestIds(documentSetId, documentsWithoutIds.size)
    val timeout = Timeout(9999999, TimeUnit.SECONDS)

    for {
      IdRequestResponse(ids) <- documentIdSupplier.ask(request)(timeout)
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
}

object WriteDocuments {
  def apply(
    documentSetId: Long,
    documentsWithoutIds: Seq[IncompleteDocument],
    metadataJson: JsObject,
    documentIdSupplier: ActorRef
  )(implicit ec: ExecutionContext): Future[Unit] = {
    new WriteDocuments(documentSetId, documentsWithoutIds, metadataJson, documentIdSupplier).execute
  }
}
