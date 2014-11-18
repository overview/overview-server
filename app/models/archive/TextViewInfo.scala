package models.archive

import java.io.InputStream
import java.nio.charset.StandardCharsets

abstract class TextViewInfo(
    suppliedId: Option[String],
    title: Option[String],
    documentId: Long,
    size: Long) extends DocumentViewInfo {

  val suppliedIdValue = suppliedId.getOrElse("")
  val titleValue = title.getOrElse("")
  
  override def archiveEntry: ArchiveEntry = {
    val nameOptions = Seq(suppliedIdValue, titleValue)
    
    val filename = nameOptions.find(!_.isEmpty).getOrElse(documentId.toString)

    ArchiveEntry(asTxt(filename), size, textInputStream(documentId) _)
  }

  private def asTxt(filename: String): String = {
    val Txt = ".txt"
    filename + Txt
  }

  private def textInputStream(documentId: Long)(): InputStream = storage.textInputStream(documentId)

  protected val storage: Storage
  protected trait Storage {
    def textInputStream(documentId: Long): InputStream
  }
}

object TextViewInfo {
  import java.io.ByteArrayInputStream
  import models.OverviewDatabase
  import org.overviewproject.models.tables.Documents
  import org.overviewproject.database.Slick.simple._
  
  def apply(suppliedId: Option[String], title: Option[String], documentId: Long, size: Long): TextViewInfo =
    new DbTextViewInfo(suppliedId, title, documentId, size)

  private class DbTextViewInfo(suppliedId: Option[String], title: Option[String], documentId: Long, size: Long)
      extends TextViewInfo(suppliedId, title, documentId, size) {

    override protected val storage = new DbStorage

    protected class DbStorage extends Storage {
      override def textInputStream(documentId: Long): InputStream =
        OverviewDatabase.withSlickSession { implicit session =>
          val q = Documents.filter(_.id === documentId).map(_.text)
          val text = q.firstOption.flatten.getOrElse("")
          new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))
        }
    }
  }
}