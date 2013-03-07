package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentTag

object DocumentTagCloner {
  private val DocumentSetIdMask: Long = 0x00000000FFFFFFFFl

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long, tagMapping: Map[Long, Long]): Unit = {
    val sourceTags = tagMapping.keys

    if (!sourceTags.isEmpty) {
      val sourceDocumentTags =
        from(Schema.documentTags)(dt => where(dt.tagId in sourceTags) select dt).toSeq

      val cloneDocumentTags: Seq[DocumentTag] =
        for {
          dt <- sourceDocumentTags
          tagId <- tagMapping.get(dt.tagId)
        } yield {
          val cloneDocumentId = (cloneDocumentSetId << 32) | (DocumentSetIdMask & dt.documentId)
          DocumentTag(cloneDocumentId, tagId)
        }

      Schema.documentTags.insert(cloneDocumentTags)
    } 
  }

}