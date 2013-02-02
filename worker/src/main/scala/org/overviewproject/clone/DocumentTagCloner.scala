package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import persistence.Schema
import org.overviewproject.tree.orm.DocumentTag

object DocumentTagCloner {

  def clone(documentMapping: Map[Long, Long], tagMapping: Map[Long, Long]) {
    val sourceDocuments = documentMapping.keys

    val sourceDocumentTags =
      from(Schema.documentTags)(dt => where(dt.documentId in sourceDocuments) select dt).toSeq

    val cloneDocumentTags: Seq[DocumentTag] = 
      sourceDocumentTags.flatMap(dt =>
        documentMapping.get(dt.documentId).flatMap { documentId =>
          tagMapping.get(dt.tagId).map { tagId =>
            DocumentTag(documentId, tagId)
          }
        })

    Schema.documentTags.insert(cloneDocumentTags)
  }
}