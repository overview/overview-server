package org.overviewproject.clone

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.{Document,DocumentTag}
import org.overviewproject.models.tables.DocumentTags

class DocumentTagClonerSpec extends DbSpecification {
  "DocumentTagCloner" should {

    trait DocumentTagContext extends DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet(id=123L)
      val cloneDocumentSet = factory.documentSet(id=124L)

      val sourceTags = Seq.fill(3) { factory.tag(documentSetId=sourceDocumentSet.id) }.sortBy(_.id)
      val cloneTags = Seq.fill(3) { factory.tag(documentSetId=cloneDocumentSet.id) }.sortBy(_.id)

      val sourceDocuments: Seq[Document] = Seq.tabulate(3) { n =>
        factory.document(documentSetId=123L, id=((123L << 32) | n))
      }

      val cloneDocuments: Seq[Document] = Seq.tabulate(3) { n =>
        factory.document(documentSetId=124L, id=((124L << 32) | n))
      }

      val tagMapping: Map[Long, Long] = sourceTags.map(_.id).zip(cloneTags.map(_.id)).toMap

      def go = DocumentTagCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id, tagMapping)

      def results: Seq[DocumentTag] = blockingDatabase.seq {
        DocumentTags
          .filter(_.tagId inSet cloneTags.map(_.id))
          .sortBy(dt => (dt.documentId, dt.tagId))
      }
    }

    "clone DocumentTags" in new DocumentTagContext {
      factory.documentTag(sourceDocuments(0).id, sourceTags(0).id)
      factory.documentTag(sourceDocuments(0).id, sourceTags(1).id)
      factory.documentTag(sourceDocuments(1).id, sourceTags(0).id)
      factory.documentTag(sourceDocuments(1).id, sourceTags(1).id)
      factory.documentTag(sourceDocuments(2).id, sourceTags(0).id)

      go

      results must beEqualTo(Seq(
        DocumentTag(cloneDocuments(0).id, cloneTags(0).id),
        DocumentTag(cloneDocuments(0).id, cloneTags(1).id),
        DocumentTag(cloneDocuments(1).id, cloneTags(0).id),
        DocumentTag(cloneDocuments(1).id, cloneTags(1).id),
        DocumentTag(cloneDocuments(2).id, cloneTags(0).id)
      ))
    }
    
    "not try to clone if there are no tags" in new DocumentTagContext {
      go
      results must beEqualTo(Seq())
    }

    "not error if there is no mapping" in new DocumentTagContext {
      override val tagMapping = Map[Long,Long]()
      go must not(throwA[Exception])
    }
  }
}
