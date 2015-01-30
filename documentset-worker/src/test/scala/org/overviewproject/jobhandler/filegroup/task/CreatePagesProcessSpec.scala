package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope

import org.overviewproject.models.{File,GroupedFileUpload}
import org.overviewproject.test.factories.PodoFactory

class CreatePagesProcessSpec extends Specification with Mockito {

  "CreatePagesProcess" should {
    
    "create a file" in new CreatePagesContext {
      val createPagesProcess = new TestCreatePagesProcess(documentSetId, upload, pdfDocument, file)
      
      val firstStep = createPagesProcess.start(documentSetId, fileGroupId, uploadedFileId)
      firstStep.execute
      
      there was one(createPagesProcess.createFile).apply(documentSetId, upload)
    }

    
    "then create pdf document with pages" in new CreatePagesContext {
      val createPagesProcess = new TestCreatePagesProcess(documentSetId, upload, pdfDocument, file)
      
      val firstStep = createPagesProcess.start(documentSetId, fileGroupId, uploadedFileId)
      val secondStep = firstStep.execute
      
      secondStep.execute
      
      there was one(createPagesProcess.pdfProcessor).loadFromBlobStorage(viewLocation)
    }
    
    "finally write the pdf pages" in new CreatePagesContext {
      val createPagesProcess = new TestCreatePagesProcess(documentSetId, upload, pdfDocument, file)
      
      val firstStep = createPagesProcess.start(documentSetId, fileGroupId, uploadedFileId)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute
      
      there was one(createPagesProcess.storage).savePages(any[Long], any[Iterable[Tuple2[Array[Byte],String]]])
      finalStep must beLike { case CreatePagesProcessComplete(d, u, f) => f must beSome(fileId) }
    }

    "store errors if exceptions occur" in new CreatePagesContext {
      val exceptionMessage = "Technically, something went wrong"
      val createPagesProcess = new FailingCreatePagesProcess(exceptionMessage)

      createPagesProcess.executeTask(documentSetId, fileGroupId, uploadedFileId) must not(throwA[Exception])

      there was one(createPagesProcess.mockStorage).saveProcessingError(documentSetId, uploadedFileId, exceptionMessage)

    }

    "insert generic error message if exception provides none" in new CreatePagesContext {
      val exceptionMessage = null
      val createPagesProcess = new FailingCreatePagesProcess(exceptionMessage)

      createPagesProcess.executeTask(documentSetId, fileGroupId, uploadedFileId) must not(throwA[Exception])

      there was one(createPagesProcess.mockStorage).saveProcessingError(documentSetId, uploadedFileId, "Unknown error")

    }

    trait CreatePagesContext extends Scope {
      val documentSetId = 1l
      val fileGroupId = 10l
      val uploadedFileId = 15l
      val fileId = 17l
      val viewLocation = "location:view"
      val upload = smartMock[GroupedFileUpload]
      val page = PdfPage(Array[Byte](10, 11, 12), "Text")
      val pdfDocument = smartMock[PdfDocument]

      val file = PodoFactory.file(id=fileId, viewLocation=viewLocation)
      pdfDocument.pages returns Seq(page)
    }
    
    class TestCreatePagesProcess(documentSetId: Long, upload: GroupedFileUpload, pdfDocument: PdfDocument, file: File) extends CreatePagesProcess {
      override val storage = smartMock[Storage]
      override val pdfProcessor = smartMock[PdfProcessor]
      override val createFile = smartMock[CreateFile]

      storage.loadUploadedFile(any) returns Some(upload) 
      createFile.apply(documentSetId, upload) returns file
      pdfProcessor.loadFromBlobStorage(any) returns pdfDocument
      
      def start(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
        startCreatePagesTask(documentSetId, uploadedFileId)
    }

    class FailingCreatePagesProcess(exceptionMessage: String) extends CreatePagesProcess {

      override protected val storage = smartMock[Storage]
      override protected val pdfProcessor = smartMock[PdfProcessor]
      override val createFile = smartMock[CreateFile]

      storage.loadUploadedFile(any) throws new RuntimeException(exceptionMessage)

      def mockStorage = storage

      def executeTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): Unit = {
        startCreatePagesTask(documentSetId, uploadedFileId).execute
      }
    }

  }

}
