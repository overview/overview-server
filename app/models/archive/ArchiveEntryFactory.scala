package models.archive

import models.DocumentFileInfo
import java.io.InputStream
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File
import org.overviewproject.models.Page
import java.io.ByteArrayInputStream

trait ArchiveEntryFactory {

  def create(document: DocumentFileInfo): Option[ArchiveEntry] = {
    createFromPage(document)
      .orElse(createFromFile(document))
  }

  private def createFromFile(document: DocumentFileInfo): Option[ArchiveEntry] =
    for {
      fileId <- document.fileId
      file <- storage.findFile(fileId)
      size <- file.viewSize
    } yield {
      ArchiveEntry(file.name, size, largeObjectInputStream(file.viewOid) _)
    }

  private def createFromPage(document: DocumentFileInfo): Option[ArchiveEntry] =
    for {
      name <- document.title
      pageId <- document.pageId
      pageNumber <- document.pageNumber
      size <- storage.findPageSize(pageId)
    } yield {
      ArchiveEntry(fileNameWithPage(name, pageNumber), size, pageDataStream(pageId) _)
    }

  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)
  
  private def pageDataStream(pageId: Long)(): InputStream = {
    def emptyStream = new ByteArrayInputStream(Array.empty)

    storage.pageDataStream(pageId).getOrElse(emptyStream)
  }

  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    s"$fileName - page $pageNumber"

  protected val storage: Storage

  protected trait Storage {
    def findFile(fileId: Long): Option[File]
    def findPageSize(pageId: Long): Option[Long]
    def largeObjectInputStream(oid: Long): InputStream
    def pageDataStream(pageId: Long): Option[InputStream]
  }

}