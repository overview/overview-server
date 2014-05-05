package org.overviewproject.jobhandler.filegroup

import scala.collection.JavaConverters._

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.stores.BaseStore

/*
 * Generates the steps needed to process uploaded files:
 * 1. Save file
 * 2. Split the PDF into pages
 * 3. Save the pages
 * 4. Extract the text from each page
 */
trait CreatePagesProcess {

  protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    LoadUploadedFile(documentSetId, fileGroupId, uploadedFileId)

  protected val storage: Storage
  protected trait Storage {
    def loadUploadedFile(uploadedFileId: Long): Option[GroupedFileUpload]
    
    def savePagesAndCleanup(createPages: Long => Iterable[Page], upload: GroupedFileUpload, documentSetId: Long): Unit
  }

  protected val pdfProcessor: PdfProcessor
  protected trait PdfProcessor {
    def loadFromDatabase(oid: Long): PdfDocument
  }

  private case class TaskInformation(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long)

  private case class LoadUploadedFile(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends FileGroupTaskStep {
    private val taskInformation = TaskInformation(documentSetId, fileGroupId, uploadedFileId)

    override def execute: FileGroupTaskStep = {
      val upload = storage.loadUploadedFile(uploadedFileId).get // throws if not found. exception handled by actor
      
      LoadPdf(taskInformation, upload)
    }
  }

  private case class LoadPdf(taskInformation: TaskInformation, upload: GroupedFileUpload) extends FileGroupTaskStep {
    override def execute: FileGroupTaskStep = {
      val pdfDocument = pdfProcessor.loadFromDatabase(upload.contentsOid)
      SavePages(taskInformation, upload, pdfDocument)
    }

    private def readPdfDocument(oid: Long): PDDocument = {
      val documentStream = new LargeObjectInputStream(oid)
      PDDocument.load(documentStream)
    }
  }

  private case class SavePages(taskInformation: TaskInformation, upload: GroupedFileUpload, pdfDocument: PdfDocument) extends FileGroupTaskStep {

    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)
    private val textStripper = new PDFTextStripper()

    override def execute: FileGroupTaskStep = {
      val pageContents = pdfDocument.pages
        
      storage.savePagesAndCleanup(createPages(pageContents, _: Long), upload, taskInformation.documentSetId)

      pdfDocument.close()

      CreatePagesProcessComplete(taskInformation.documentSetId, taskInformation.fileGroupId, taskInformation.uploadedFileId)
    }

    private def createPages(pageContents: Iterable[PdfPage], fileId: Long): Iterable[Page] =
      pageContents.view.zipWithIndex.map { case (p, i) =>
        createPageFromContent(fileId, p, i)
      }

    private def createPageFromContent(fileId: Long, content: PdfPage, pageNumber: Int): Page =
      Page(fileId, pageNumber, 1, Some(content.data), Some(content.text))

  }

}

