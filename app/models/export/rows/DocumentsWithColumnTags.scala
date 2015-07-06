package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import org.overviewproject.metadata.{Metadata,MetadataSchema}
import org.overviewproject.models.Tag

object DocumentsWithColumnTags {
  def apply(metadataSchema: MetadataSchema, result: Enumerator[DocumentForCsvExport], tags: Seq[Tag]): Rows = {
    val metadataFields: Array[String] = metadataSchema.fields.map(_.name).toArray

    val headers: Array[String] = Array("id", "title", "text", "url") ++ metadataFields ++ tags.map(_.name)

    val allTagIds = tags.map(_.id).toArray // fastest implementation

    val rows: Enumerator[Array[String]] = result.map { document =>
      val metadata = Metadata(metadataSchema, document.metadataJson)
      val tagIds = document.tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(document.suppliedId, document.title, document.text, document.url)
        .++(metadataFields.map(metadata.getString(_)))
        .++(allTagIds.map(id => if (tagIds(id)) "1" else ""))
    }

    Rows(headers, rows)
  }
}
