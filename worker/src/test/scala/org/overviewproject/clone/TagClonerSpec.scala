package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ DocumentSet, Tag }

class TagClonerSpec extends DbSpecification {

  step(setupDb)

  "TagCloner" should {

    trait TagContext extends DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._

      var sourceDocumentSetId: Long = _
      var cloneDocumentSetId: Long = _
      var sourceTags: Seq[Tag] = _
      var cloneTags: Seq[Tag] = _
      var tagIdMapping: Map[Long, Long] = _
      
      override def setupWithDb = {
        sourceDocumentSetId = Schema.documentSets.insert(DocumentSet(title = "TagClonerSpec"))
        cloneDocumentSetId = Schema.documentSets.insert(DocumentSet(title = "CloneTagClonerSpec"))

        val tags = Seq.tabulate(10)(i => Tag(sourceDocumentSetId, "tag-" + i, "ffffff"))
        Schema.tags.insert(tags)
        tagIdMapping = TagCloner.clone(sourceDocumentSetId, cloneDocumentSetId)

        sourceTags = Schema.tags.where(t => t.documentSetId === sourceDocumentSetId).toSeq
        cloneTags = Schema.tags.where(t => t.documentSetId === cloneDocumentSetId).toSeq

      }
    }

    "clone tags" in new TagContext {
      val cloneData = cloneTags.map(t => (t.name, t.color))
      val expectedTagData = sourceTags.map(t => (t.name, t.color))

      cloneData must containTheSameElementsAs(expectedTagData)
    }

    "map source tag ids to clone tag ids" in new TagContext {
      val mappedIds = sourceTags.flatMap(t => tagIdMapping.get(t.id))
      
      mappedIds must containTheSameElementsAs(cloneTags.map(_.id))
    }
  }

  step(shutdownDb)
}
