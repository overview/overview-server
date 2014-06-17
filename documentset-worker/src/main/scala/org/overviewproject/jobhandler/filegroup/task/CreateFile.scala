package org.overviewproject.jobhandler.filegroup.task

import scala.util.control.Exception._
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID

trait CreateFile {
  val PdfMagicNumber: Array[Byte] = "%PDF".getBytes

  def apply(upload: GroupedFileUpload): File = {
    val stream = storage.getLargeObjectInputStream(upload.contentsOid)

    val magicNumber = peekAtMagicNumber(stream)

    if (magicNumber.sameElements(PdfMagicNumber)) storage.createFile(upload.name, upload.contentsOid)
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
    def createFile(name: String, oid: Long): File
    def createFileWithPdfView(upload: GroupedFileUpload, viewStream: InputStream): File    
  }

  protected trait DocumentConverter {
    def convertStreamToPdf[T](guid: UUID, documentStream: InputStream)(f: InputStream => T): T

  }
}