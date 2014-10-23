package models.archive

import models.DocumentFileInfo
import java.io.InputStream
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File

trait ArchiveEntryFactory {

  def create(document: DocumentFileInfo): Option[ArchiveEntry] = {
    
    for {
      fileId <- document.fileId
      file <- storage.findFile(fileId)
      size <- file.viewSize
    } yield {
      ArchiveEntry(file.name, size, largeObjectInputStream(file.viewOid) _)
    }
  }
  
  protected val storage: Storage 
  
  protected trait Storage {
    def findFile(fileId: Long): Option[File]
    def largeObjectInputStream(oid: Long): InputStream
  }
  
  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)
}