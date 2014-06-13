package org.overviewproject.jobhandler.filegroup.task

import scala.collection.JavaConverters._
import scala.util.control.Exception._
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.stores.BaseStore


/*
 * Generates the steps needed to process uploaded files:
 * 1. Save file
 * 2. Split the PDF into pages
 * 3. Save the pages
 * 4. Extract the text from each page
 * 
 * If an Exception is thrown, processing completes, but the error is saved as a `DocumentProcessingError`
 * @todo store the error in `Page`
 */
trait CreatePagesProcess {

  protected case class TaskInformation(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long)

  protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    LoadUploadedFile(TaskInformation(documentSetId, fileGroupId, uploadedFileId))

  protected val storage: Storage
  protected trait Storage {
    def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload]

    def savePagesAndCleanup(createPages: Long => Iterable[Page], upload: GroupedFileUpload, documentSetId: Long): Unit

    def saveProcessingError(documentSetId: Long, uploadedFileId: Long, errorMessage: String): Unit
  }

  protected trait ErrorSavingTaskStep extends FileGroupTaskStep {
    private val UnknownError = "Unknown error"

    protected val taskInformation: TaskInformation

    override def execute: FileGroupTaskStep = handling(classOf[Exception]) by { e =>
      val errorMessage = Option(e.getMessage).getOrElse(UnknownError)

      storage.saveProcessingError(taskInformation.documentSetId, taskInformation.uploadedFileId, errorMessage)
      CreatePagesProcessComplete(taskInformation.documentSetId, taskInformation.fileGroupId, taskInformation.uploadedFileId)
    } apply executeTaskStep

    protected def executeTaskStep: FileGroupTaskStep
  }

  protected val pdfProcessor: PdfProcessor
  protected trait PdfProcessor {
    def loadFromDatabase(oid: Long): PdfDocument
  }

  private case class LoadUploadedFile(taskInformation: TaskInformation) extends ErrorSavingTaskStep {

    override def executeTaskStep: FileGroupTaskStep = {
      val upload = storage.loadUploadedFile(taskInformation.uploadedFileId).get // throws if not found. exception handled by actor

      LoadPdf(taskInformation, upload)
    }
  }

  private case class LoadPdf(taskInformation: TaskInformation, upload: GroupedFileUpload) extends ErrorSavingTaskStep {
    override def executeTaskStep: FileGroupTaskStep = {
      val pdfDocument = pdfProcessor.loadFromDatabase(upload.contentsOid)
      SavePages(taskInformation, upload, pdfDocument)
    }
  }

  private case class SavePages(taskInformation: TaskInformation, upload: GroupedFileUpload, pdfDocument: PdfDocument) extends ErrorSavingTaskStep {

    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)
    private val textStripper = new PDFTextStripper()

    override def executeTaskStep: FileGroupTaskStep = {
      val pageContents = pdfDocument.pages

      storage.savePagesAndCleanup(createPages(pageContents, _: Long), upload, taskInformation.documentSetId)

      pdfDocument.close()

      CreatePagesProcessComplete(taskInformation.documentSetId, taskInformation.fileGroupId, taskInformation.uploadedFileId)
    }

    override def cancel: Unit = pdfDocument.close()

    private def createPages(pageContents: Iterable[PdfPage], fileId: Long): Iterable[Page] = {
      var pageNumber = 0

      for (page <- pageContents) yield {
        pageNumber += 1
        createPageFromContent(fileId, page, pageNumber)
      }
    }

    private def createPageFromContent(fileId: Long, content: PdfPage, pageNumber: Int): Page =
      Page(fileId, pageNumber, 1, Some(content.data), Some(content.text))

  }

}

