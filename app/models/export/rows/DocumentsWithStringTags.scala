package models.export.rows

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.FinderResult

import models.OverviewDocument

class DocumentsWithStringTags(finderResult: FinderResult[(Document,Option[String])]) extends Rows {
  override def headers : Iterable[String] = Seq("id", "title", "text", "url", "tags")

  override def rows : Iterable[Iterable[Any]] = {
    finderResult.map { case (ormDocument, tags) =>
      val document = OverviewDocument(ormDocument)
      Vector(
        document.suppliedId.getOrElse(""),
        document.title.getOrElse(""),
        document.text.getOrElse(""),
        document.url.getOrElse(""),
        tags.getOrElse("")
      )
    }
  }
}
