package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import org.overviewproject.models.Tag

object DocumentsWithColumnTags {
  def apply(result: Enumerator[DocumentForCsvExport], tags: Seq[Tag]): Rows = {
    val headers: Array[String] = Array("id", "title", "text", "url") ++ tags.map(_.name)

    val allTagIds = tags.map(_.id).toArray // fastest implementation

    val rows: Enumerator[Array[String]] = result.map { document =>
      val tagIds = document.tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(
        document.suppliedId,
        document.title,
        document.text,
        document.url
      ) ++ allTagIds.map(id => if (tagIds(id)) "1" else "")
    }

    Rows(headers, rows)
  }
}
