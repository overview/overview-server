package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.File
import java.io.InputStream

trait CreateFile {
  val PdfMagicNumber: Array[Byte] = "%PDF".getBytes
  
  def apply(name: String, oid: Long): File = {
    val stream = storage.getLargeObjectInputStream(oid)
    
    val magicNumber = peekAtMagicNumber(stream)
    
    if (magicNumber.sameElements(PdfMagicNumber)) File(1, oid, oid, name)
    else converter.createFileWithPdfView(name, oid, stream)

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
  
  protected val storage: Storage 
  protected val converter: DocumentConverter
  
  protected trait Storage {
    def getLargeObjectInputStream(oid: Long): InputStream 
  }
  
  protected trait DocumentConverter {
    def createFileWithPdfView(name: String, contentsOid: Long, inputStream: InputStream): File
  }
}