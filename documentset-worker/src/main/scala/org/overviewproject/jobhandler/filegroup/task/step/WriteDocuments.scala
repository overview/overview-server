package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.util.BulkDocumentWriter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.models.Document
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.models.TempDocumentSetFile

trait WriteDocuments extends TaskStep {

  protected val storage: Storage
  protected val bulkDocumentWriter: BulkDocumentWriter
  protected val documents: Seq[Document]

  // FIXME: Request document ids
  override def execute: Future[TaskStep] = for {
    docsAdded <- Future.sequence(documents.map(bulkDocumentWriter.addAndFlushIfNeeded)) // FIXME: should be done in serial
    batchFlushed <- bulkDocumentWriter.flush
    deleted <- storage.deleteTempDocumentSetFiles(documents)
  } yield FinalStep

  protected trait Storage {
    def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int]
  }
}

object WriteDocuments {

  def apply(document: Document): WriteDocuments = new WriteDocumentsImpl(Seq(document))
  
  private class WriteDocumentsImpl(
    override protected val documents: Seq[Document]) extends WriteDocuments {

    override protected val bulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex // Thread safe?
    override protected val storage: Storage = new SlickStorage
    
    private class SlickStorage extends Storage with SlickSessionProvider {
      import org.overviewproject.database.Slick.simple._
      import org.overviewproject.models.tables.TempDocumentSetFiles
      
      override def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int] = db { implicit session =>
        val fileIds = documents.flatMap(_.fileId)
        TempDocumentSetFiles.filter(_.fileId inSet fileIds).delete
      }
    } 
  }
}