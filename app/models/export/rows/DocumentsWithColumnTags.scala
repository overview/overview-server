package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import com.overviewdocs.metadata.{Metadata,MetadataSchema}
import com.overviewdocs.models.{Document,Tag}

object DocumentsWithColumnTags {
  def apply(metadataSchema: MetadataSchema, result: Enumerator[(Document,Seq[Long])], tags: Seq[Tag]): Rows = {
    val metadataFields: Array[String] = metadataSchema.fields.map(_.name).toArray

    val headers: Array[String] = Array("id", "title", "text", "url") ++ metadataFields ++ tags.map(_.name)

    val allTagIds = tags.map(_.id).toArray // fastest implementation

    val rows: Enumerator[Array[String]] = result.map { case (document, tagIds) =>
      val metadata = Metadata(metadataSchema, document.metadataJson)
      val tagIdsSet = tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(document.suppliedId, document.title, document.text, document.url.getOrElse(""))
        .++(metadataFields.map(metadata.getString(_)))
        .++(allTagIds.map(id => if (tagIdsSet(id)) "1" else ""))
    }

    Rows(headers, rows)
  }
}
