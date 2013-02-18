package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentTag

object DocumentTagCloner {

  def clone(documentMapping: Map[Long, Long], tagMapping: Map[Long, Long]) {
    val sourceDocuments = documentMapping.keys

    val sourceDocumentTags =
      from(Schema.documentTags)(dt => where(dt.documentId in sourceDocuments) select dt).toSeq

    val cloneDocumentTags: Seq[DocumentTag] = 
      for {
        dt <- sourceDocumentTags
        documentId <- documentMapping.get(dt.documentId)
        tagId <- tagMapping.get(dt.tagId)
      } yield DocumentTag(documentId, tagId)

    Schema.documentTags.insert(cloneDocumentTags)
  }
}