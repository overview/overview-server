package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.CreatePagesProcess

class CreatePagesProcessSpec extends Specification with Mockito {

  "CreatePagesProcess" should {

    "store errors if exceptions occur" in {
      val documentSetId = 1l
      val fileGroupId = 10l
      val uploadedFileId = 15l

      val exceptionMessage = "Technically, something went wrong"
      val createPagesProcess = new TestCreatePagesProcess(exceptionMessage)

      createPagesProcess.executeTask(documentSetId, fileGroupId, uploadedFileId) must not(throwA[Exception])

      there was one(createPagesProcess.mockStorage).saveProcessingError(documentSetId, uploadedFileId, exceptionMessage)

    }

    "insert generic error message if exception provides none" in {
      val documentSetId = 1l
      val fileGroupId = 10l
      val uploadedFileId = 15l

      val exceptionMessage = null
      val createPagesProcess = new TestCreatePagesProcess(exceptionMessage)

      createPagesProcess.executeTask(documentSetId, fileGroupId, uploadedFileId) must not(throwA[Exception])

      there was one(createPagesProcess.mockStorage).saveProcessingError(documentSetId, uploadedFileId, "Unknown error")

    }

    class TestCreatePagesProcess(exceptionMessage: String) extends CreatePagesProcess {

      override protected val storage = mock[Storage]
      override protected val pdfProcessor = mock[PdfProcessor]

      storage.loadUploadedFile(any) throws new RuntimeException(exceptionMessage)

      def mockStorage = storage

      def executeTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): Unit = {
        startCreatePagesTask(documentSetId, fileGroupId, uploadedFileId).execute
      }
    }

  }

}