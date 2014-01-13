package models.export.rows

import org.overviewproject.tree.orm.{Document,Tag}
import org.overviewproject.tree.orm.finders.FinderResult

import models.OverviewDocument

class DocumentsWithColumnTags(finderResult: FinderResult[(Document,Option[String])], tagFinderResult: FinderResult[Tag]) extends Rows {
  override def headers : Iterable[String] = {
    val tagNames = tagFinderResult.map(_.name).toIterable
    Seq("id", "title", "text", "url") ++ tagNames
  }

  override def rows : Iterable[Iterable[Any]] = {
    val allTagIds = tagFinderResult.map(_.id).toArray // fastest implementation

    finderResult.map { case (ormDocument, tagsString) =>
      val document = OverviewDocument(ormDocument)
      // "1,2,3" -> Set[Long](1, 2, 3)
      val tagIdSet = tagsString.getOrElse("").split(',').collect { case s: String if s.length > 0 => s.toLong }.toSet

      (
        Array[Any](
          document.suppliedId.getOrElse(""),
          document.title.getOrElse(""),
          document.text.getOrElse(""),
          document.url.getOrElse("")
        )
        ++ allTagIds.map(id => if (tagIdSet.contains(id)) "1" else "").toArray[Any]
      ).toSeq
    }
  }
}
