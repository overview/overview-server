package models.export.rows

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import com.overviewdocs.metadata.{Metadata,MetadataSchema}
import com.overviewdocs.models.Tag

object DocumentsWithStringTags {
  def apply(metadataSchema: MetadataSchema, result: Enumerator[DocumentForCsvExport], tags: Seq[Tag]): Rows = {
    val metadataFields: Array[String] = metadataSchema.fields.map(_.name).toArray

    val headers: Array[String] = Array("id", "title", "text", "url") ++ metadataFields ++ Array("tags")

    val rows: Enumerator[Array[String]] = result.map { document: DocumentForCsvExport =>
      val metadata = Metadata(metadataSchema, document.metadataJson)
      val tagIds = document.tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(document.suppliedId, document.title, document.text, document.url)
        .++(metadataFields.map(metadata.getString(_)))
        .++(Array(tags.collect{ case tag if tagIds(tag.id) => tag.name }.mkString(",")))
    }

    Rows(headers, rows)
  }
}
