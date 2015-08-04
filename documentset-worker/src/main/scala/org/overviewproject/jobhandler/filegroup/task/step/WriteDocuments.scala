package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.Document
import com.overviewdocs.models.TempDocumentSetFile
import com.overviewdocs.util.BulkDocumentWriter
import com.overviewdocs.searchindex.ElasticSearchIndexClient
import com.overviewdocs.searchindex.TransportIndexClient

/**
 * Write documents to the database and index them in ElasticSearch.
 */
trait WriteDocuments extends UploadedFileProcessStep {

  override protected val documentSetId: Long
  override protected val filename: String

  protected val storage: Storage
  protected val bulkDocumentWriter: BulkDocumentWriter
  protected val searchIndex: ElasticSearchIndexClient

  protected val documents: Seq[Document]

  override protected def doExecute: Future[TaskStep] = {
    val write = writeDocuments
    val index = indexDocuments

    for {
      writeResult <- write
      indexResult <- index
      deleted <- storage.deleteTempDocumentSetFiles(documents)
    } yield FinalStep

  }

  protected trait Storage {
    def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int]
  }

  private def writeDocuments: Future[Unit] =
    for {
      docsAdded <- Future.sequence(documents.map(bulkDocumentWriter.addAndFlushIfNeeded))
    } yield {}

  private def indexDocuments: Future[Unit] = searchIndex.addDocuments(documents)

}

object WriteDocuments {

  def apply(documentSetId: Long, filename: String, documents: Seq[Document],
            bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext): WriteDocuments =
    new WriteDocumentsImpl(documentSetId, filename, documents, bulkDocumentWriter)

  private class WriteDocumentsImpl(
    override protected val documentSetId: Long,
    override protected val filename: String,
    override protected val documents: Seq[Document],
    override protected val bulkDocumentWriter: BulkDocumentWriter)
   (override implicit protected val executor: ExecutionContext) extends WriteDocuments {

    override protected val searchIndex = TransportIndexClient.singleton

    override protected val storage: Storage = new SlickStorage

    private class SlickStorage extends Storage with HasDatabase {
      import database.api._
      import com.overviewdocs.models.tables.TempDocumentSetFiles

      override def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int] = {
        val fileIds = documents.flatMap(_.fileId)
        database.run(TempDocumentSetFiles.filter(_.fileId inSet fileIds).delete)
      }
    }
  }
}
