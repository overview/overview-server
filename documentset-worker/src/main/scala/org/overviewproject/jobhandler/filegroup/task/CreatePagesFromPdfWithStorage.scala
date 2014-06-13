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


trait CreatePagesFromPdfWithStorage extends CreatePagesProcess {

  override protected val storage = DatabaseStorage()
  override protected val pdfProcessor = PdfBoxProcessor()

  private object DatabaseStorage {
    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    def apply(): Storage = new Storage {

      def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload] = Database.inTransaction {
        GroupedFileUploadFinder.byId(uploadedFileId).headOption
      }

      def savePagesAndCleanup(createPages: Long => Iterable[Page], upload: GroupedFileUpload, documentSetId: Long): Unit = Database.inTransaction {
        val file = FileStore.insertOrUpdate(File(1, upload.contentsOid, upload.name))
        tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))

        val pages = createPages(file.id)

        pages.foreach(pageStore.insertOrUpdate) // Batch insert would read all pages into memory, possibly leading to OutOfMemoryException
        
        GroupedFileUploadStore.delete(GroupedFileUploadFinder.byId(upload.id).toQuery)
      }

      def saveProcessingError(documentSetId: Long, uploadedFileId: Long, errorMessage: String): Unit = Database.inTransaction {
        val documentProcessingErrorStore = BaseStore(Schema.documentProcessingErrors)
        val error = DocumentProcessingError(documentSetId, s"UploadedFileId: $uploadedFileId", errorMessage)

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