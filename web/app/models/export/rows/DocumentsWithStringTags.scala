// Generates a CSV export of a document set with all tags in one column, separated by commas
package models.export.rows

import akka.stream.scaladsl.Source
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.metadata.{Metadata,MetadataSchema}
import com.overviewdocs.models.{Document,Tag}

object DocumentsWithStringTags {
  def apply(metadataSchema: MetadataSchema, result: Source[(Document, immutable.Seq[Long]), akka.NotUsed], tags: immutable.Seq[Tag]): Rows = {
    val metadataFields: Array[String] = metadataSchema.fields.map(_.name).toArray

    val headers: Array[String] = Array("id", "title", "text", "url") ++ metadataFields ++ Array("tags")

    val rows: Source[Array[String], akka.NotUsed] = result.map { case (document, tagIds) =>
      val metadata = Metadata(metadataSchema, document.metadataJson)
      val tagIdsSet = tagIds.toSet // admittedly, a pretty slow approach; array merge would be faster :)
      Array(document.suppliedId, document.title, document.text, document.url.getOrElse(""))
        .++(metadataFields.map(metadata.getString(_)))
        .++(Array(tags.collect{ case tag if tagIdsSet(tag.id) => tag.name }.mkString(",")))
    }

    Rows(headers, rows)
  }
}
