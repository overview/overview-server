package org.overviewproject.jobhandler.filegroup.task

import scala.util.control.Exception._
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID
import org.overviewproject.database.Database
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.TempDocumentSetFile

trait CreateFile {
  val PdfMagicNumber: Array[Byte] = "%PDF".getBytes

  def apply(documentSetId: Long, upload: GroupedFileUpload): File = {
    val stream = storage.getLargeObjectInputStream(upload.contentsOid)

    val magicNumber = peekAtMagicNumber(stream)

    if (magicNumber.sameElements(PdfMagicNumber)) storage.createFile(documentSetId, upload.name, upload.contentsOid)
    else converter.convertStreamToPdf(upload.guid, stream) { pdfStream =>
      storage.createFileWithPdfView(upload, pdfStream)
    }

  }

  private def peekAtMagicNumber(inputStream: InputStream): Array[Byte] = {
    val magicNumberBytes = 4
    val allowedReadAhead = magicNumberBytes * 2
    val magicNumber = new Array[Byte](4)

    inputStream.mark(allowedReadAhead)
    inputStream.read(magicNumber, 0, magicNumberBytes)
    inputStream.reset

    magicNumber
  }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = storage.getLargeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }

  protected val storage: Storage
  protected val converter: DocumentConverter

  protected trait Storage {
    def getLargeObjectInputStream(oid: Long): InputStream
    def createFile(documentSetId: Long, name: String, oid: Long): File
    def createFileWithPdfView(upload: GroupedFileUpload, viewStream: InputStream): File
  }

  protected trait DocumentConverter {
    def convertStreamToPdf[T](guid: UUID, documentStream: InputStream)(f: InputStream => T): T

  }
}

object CreateFile extends CreateFile {
  override protected val storage: Storage = new DatabaseStorage
  override protected val converter: DocumentConverter = new LibreOfficeDocumentConverter

  class DatabaseStorage extends Storage {
    private val tempDocumentSetFileStore = new BaseStore(Schema.tempDocumentSetFiles)
    
    override def getLargeObjectInputStream(oid: Long): InputStream = new LargeObjectInputStream(oid)
    
    override def createFile(documentSetId: Long, name: String, oid: Long): File = Database.inTransaction {
      val file = FileStore.insertOrUpdate(File(1, oid, oid, name))
      tempDocumentSetFileStore.insertOrUpdate(TempDocumentSetFile(documentSetId, file.id))
      
      file
    }

    override def createFileWithPdfView(upload: GroupedFileUpload, viewStream: InputStream): File = ???
  }

  class LibreOfficeDocumentConverter extends DocumentConverter {
    override def convertStreamToPdf[T](guid: UUID, documentStream: InputStream)(f: InputStream => T): T = ???
  }
}