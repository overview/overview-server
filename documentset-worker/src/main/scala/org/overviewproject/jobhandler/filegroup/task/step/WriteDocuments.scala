package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.Document
import org.overviewproject.models.TempDocumentSetFile
import org.overviewproject.util.BulkDocumentWriter
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.searchindex.TransportIndexClient

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

  def apply(documentSetId: Long, filename: String,
            documents: Seq[Document], bulkDocumentWriter: BulkDocumentWriter): WriteDocuments =
    new WriteDocumentsImpl(documentSetId, filename, documents, bulkDocumentWriter)

  private class WriteDocumentsImpl(
    override protected val documentSetId: Long,
    override protected val filename: String,
    override protected val documents: Seq[Document],
    override protected val bulkDocumentWriter: BulkDocumentWriter) extends WriteDocuments {

    override protected val searchIndex = TransportIndexClient.singleton

    override protected val storage: Storage = new SlickStorage

    private class SlickStorage extends Storage with DatabaseProvider {
      import databaseApi._
      import org.overviewproject.models.tables.TempDocumentSetFiles

      override def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int] = {
        val fileIds = documents.flatMap(_.fileId)
        database.run(TempDocumentSetFiles.filter(_.fileId inSet fileIds).delete)
      }
    }
  }
}
