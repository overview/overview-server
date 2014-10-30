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
      name <- document.title
      fileId <- document.fileId
      file <- storage.findFile(fileId)
      size <- file.viewSize
    } yield {
      ArchiveEntry(asPdf(name), size, largeObjectInputStream(file.viewOid) _)
    }

  private def createFromPage(document: DocumentFileInfo): Option[ArchiveEntry] =
    for {
      name <- document.title
      pageId <- document.pageId
      pageNumber <- document.pageNumber
      size <- storage.findPageSize(pageId)
    } yield {
      ArchiveEntry(fileNameWithPage(removePdf(name), pageNumber), size, pageDataStream(pageId) _)
    }

  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)

  // If the pageId is invalid, NoSuchElementException will be thrown
  // We could return an empty stream, but then the size would be wrong
  // so the archive would be corrupt anyway.
  private def pageDataStream(pageId: Long)(): InputStream =
    storage.pageDataStream(pageId).get

  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    asPdf(s"$fileName - p. $pageNumber")

  private def removePdf(fileName: String): String = {
    val caseInsensitivePdfExtension = "(?i)\\.pdf$"
    fileName.replaceAll(caseInsensitivePdfExtension, "")
  }
  
  private def asPdf(fileName: String): String = {
    val Pdf = ".pdf"
    if (fileName.toLowerCase endsWith Pdf) fileName
    else fileName + Pdf
  }

  protected val storage: Storage

  protected trait Storage {
    def findFile(fileId: Long): Option[File]
    def findPageSize(pageId: Long): Option[Long]
    def largeObjectInputStream(oid: Long): InputStream
    def pageDataStream(pageId: Long): Option[InputStream]
  }

}