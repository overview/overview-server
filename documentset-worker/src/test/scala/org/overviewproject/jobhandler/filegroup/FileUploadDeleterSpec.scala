package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.FileUploadDeleter

class FileUploadDeleterSpec extends Specification with Mockito {

  "FileUploadDeleter" should {

    "delete all data related to upload" in {
      val documentSetId = 1l
      val fileGroupId = 10l
      val fileUploadDeleter = new TestFileUploadDeleter

      fileUploadDeleter.deleteFileUpload(documentSetId, fileGroupId)
      
      there was one(fileUploadDeleter.mockStorage).deleteGroupedFileUploads(fileGroupId)
      there was one(fileUploadDeleter.mockStorage).deletePages(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteFiles(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteTempDocumentSetFiles(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteDocumentProcessingErrors(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteDocumentSetCreationJob(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteFileGroup(fileGroupId)
      there was one(fileUploadDeleter.mockStorage).deleteDocuments(documentSetId)
      there was one(fileUploadDeleter.mockStorage).deleteDocumentSet(documentSetId)
    }
  }

  class TestFileUploadDeleter extends FileUploadDeleter {
    val mockStorage = mock[Storage]
    	
    override val storage = mockStorage
  }
}

