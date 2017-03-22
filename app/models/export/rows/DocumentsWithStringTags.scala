// Generates a CSV export of a document set with all tags in one column, separated by commas
package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import com.overviewdocs.metadata.{Metadata,MetadataSchema}
import com.overviewdocs.models.{Document,Tag}

object DocumentsWithStringTags {
  def apply(metadataSchema: MetadataSchema, result: Enumerator[(Document,Seq[Long])], tags: Seq[Tag]): Rows = {
    val metadataFields: Array[String] = metadataSchema.fields.map(_.name).toArray

    val headers: Array[String] = Array("id", "title", "text", "url") ++ metadataFields ++ Array("tags")

    val rows: Enumerator[Array[String]] = result.map { case (document, tagIds) =>
      val metadata = Metadata(metadataSchema, document.metadataJson)
      val tagIdsSet = tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(document.suppliedId, document.title, document.text, document.url.getOrElse(""))
        .++(metadataFields.map(metadata.getString(_)))
        .++(Array(tags.collect{ case tag if tagIdsSet(tag.id) => tag.name }.mkString(",")))
    }

    Rows(headers, rows)
  }
}
