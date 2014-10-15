package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.database.Database
import org.overviewproject.database.orm.Schema
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.database.orm.stores.GroupedFileUploadStore
import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.DocumentProcessingError


/** Provides database storage and pdfbox pdfProcessor implementations for [[CreatePagesProcess]] */
trait CreatePagesFromPdfWithStorage extends CreatePagesProcess {

  override protected val storage = DatabaseStorage()
  override protected val pdfProcessor = PdfBoxProcessor()
  override protected val createFile = CreateFile
  
  private object DatabaseStorage {
    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    def apply(): Storage = new Storage {

      def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload] = Database.inTransaction {
        GroupedFileUploadFinder.byId(uploadedFileId).headOption
      }

      def deleteUploadedFile(upload: GroupedFileUpload): Unit = Database.inTransaction {
    	import org.overviewproject.postgres.SquerylEntrypoint._
        
    	GroupedFileUploadStore.delete(upload.id)
      } 
      
      def savePages(pages: Iterable[Page]): Unit = Database.inTransaction {
        pages.foreach(pageStore.insertOrUpdate) // Batch insert would read all pages into memory, possibly leading to OutOfMemoryException
      }
      

      def saveProcessingError(documentSetId: Long, uploadedFileId: Long, errorMessage: String): Unit = Database.inTransaction {
        val upload = GroupedFileUploadFinder.byId(uploadedFileId).headOption
        val filename = upload.map(_.name).getOrElse(s"Uploaded File Id: $uploadedFileId")
        
        val documentProcessingErrorStore = BaseStore(Schema.documentProcessingErrors)
        val error = DocumentProcessingError(documentSetId, filename, errorMessage)

        documentProcessingErrorStore.insertOrUpdate(error)
      }
    }
  }

  private object PdfBoxProcessor {
    def apply(): PdfProcessor = new PdfProcessor {
      override def loadFromDatabase(oid: Long): PdfDocument = new PdfBoxDocument(oid)
    }
  }
}