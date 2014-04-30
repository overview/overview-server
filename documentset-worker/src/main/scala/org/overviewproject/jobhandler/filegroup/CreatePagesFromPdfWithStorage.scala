package org.overviewproject.jobhandler.filegroup

import org.overviewproject.database.Database
import org.overviewproject.database.orm.Schema
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.database.orm.stores.GroupedFileUploadStore

import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.stores.BaseStore

trait CreatePagesFromPdfWithStorage extends CreatePagesProcess {

  override protected val storage = DatabaseStorage()

  private object DatabaseStorage {
    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)

    def apply(): Storage = new Storage {

      def createFileFromUpload(documentSetId: Long, uploadedFileId: Long): Option[File] = Database.inTransaction {
        GroupedFileUploadFinder.byId(uploadedFileId).headOption.map { u =>
          val file = FileStore.insertOrUpdate(File(1, u.contentsOid, u.name))
          tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))
          
          file
        }
      }

      def savePagesAndCleanup(filePages: Seq[Page], uploadedFileId: Long): Unit = Database.inTransaction {
        pageStore.insertBatch(filePages)
        GroupedFileUploadStore.delete(GroupedFileUploadFinder.byId(uploadedFileId).toQuery)
      }
    }
  }
}