package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import org.overviewproject.models.Tag

object DocumentsWithStringTags {
  def apply(result: Enumerator[DocumentForCsvExport], tags: Seq[Tag]): Rows = {
    val headers: Array[String] = Array("id", "title", "text", "url", "tags")

    val rows: Enumerator[Array[String]] = result.map { document: DocumentForCsvExport =>
      val tagIds = document.tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(
        document.suppliedId,
        document.title,
        document.text,
        document.url,
        tags.collect{ case tag if tagIds(tag.id) => tag.name }.mkString(",")
      )
    }

    Rows(headers, rows)
  }
}
