package org.overviewproject.jobhandler.filegroup

import scala.collection.JavaConverters._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.tree.orm.File
import org.overviewproject.database.orm.stores.FileStore
import org.apache.pdfbox.pdmodel.PDDocument
import org.overviewproject.postgres.LargeObjectInputStream
import org.apache.pdfbox.util.Splitter
import java.io.ByteArrayOutputStream
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.util.Textify
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import org.apache.pdfbox.pdfwriter.COSWriter
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.database.orm.stores.GroupedFileUploadStore

/*
 * Generates the steps needed to process uploaded files:
 * 1. Save file
 * 2. Split the PDF into pages
 * 3. Save the pages
 * 4. Extract the text from each page
 */
trait CreatePagesProcess {

  protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    SaveFile(documentSetId, fileGroupId, uploadedFileId)

  private case class TaskInformation(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long)

  private case class SaveFile(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends FileGroupTaskStep {
    private val taskInformation = TaskInformation(documentSetId, fileGroupId, uploadedFileId)

    override def execute: FileGroupTaskStep = {
      Database.inTransaction {
        GroupedFileUploadFinder.byId(uploadedFileId).headOption.map { upload =>
          val file = FileStore.insertOrUpdate(File(1, upload.contentsOid, upload.name))

          SplitPdf(taskInformation, file)
        }.get
      }
    }
  }

  private case class SplitPdf(taskInformation: TaskInformation, file: File) extends FileGroupTaskStep {
    override def execute: FileGroupTaskStep = {
      val pdfDocument = readPdfDocument(file.contentsOid)
      val splitter = new Splitter()
      splitter.setSplitAtPage(1)
      val pages: Seq[PDDocument] = splitter.split(pdfDocument).asScala
      SavePages(taskInformation, file.id, pdfDocument, pages)
    }

    private def readPdfDocument(oid: Long): PDDocument = {
      val documentStream = new LargeObjectInputStream(oid)
      PDDocument.load(documentStream)
    }
  }

  private case class SavePages(taskInformation: TaskInformation, fileId: Long, pdfDocument: PDDocument,
                               pages: Seq[PDDocument]) extends FileGroupTaskStep {

    private val pageStore = new BaseStore(Schema.pages)
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)
    private val textStripper = new PDFTextStripper()

    override def execute: FileGroupTaskStep = {
      val filePages = pages.iterator.zipWithIndex.map {
        case (p, i) =>
          val outputStream = new ByteArrayOutputStream()
          p.save(outputStream)
          outputStream.close()
          val data: Array[Byte] = outputStream.toByteArray()

          val rawText: String = textStripper.getText(p)
          p.close()
          val text: String = textify(rawText)
          Page(fileId, i, 1, Some(data), Some(text))
      }

      Database.inTransaction {
        tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(taskInformation.documentSetId, fileId))
        pageStore.insertBatch(filePages.toIterable)
        GroupedFileUploadStore.delete(GroupedFileUploadFinder.byId(taskInformation.uploadedFileId).toQuery)
      }

      pdfDocument.close()      
      
      CreatePagesProcessComplete(taskInformation.fileGroupId, taskInformation.uploadedFileId)
    }

    private def textify(rawText: String): String = Textify(rawText)
  }

}

