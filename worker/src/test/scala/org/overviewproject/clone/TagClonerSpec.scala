package org.overviewproject.clone

import org.overviewproject.persistence.{ Schema, Tag }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification

class TagClonerSpec extends DbSpecification {

  step(setupDb)

  "TagCloner" should {

    trait TagContext extends DbTestContext {
      var sourceDocumentSetId: Long = _
      var cloneDocumentSetId: Long = _
      var sourceTags: Seq[Tag] = _
      var cloneTags: Seq[Tag] = _
      var tagIdMapping: Map[Long, Long] = _
      
      override def setupWithDb = {
        sourceDocumentSetId = insertDocumentSet("TagClonerSpec")
        cloneDocumentSetId = insertDocumentSet("CloneTagClonerSpec")

        val tags = Seq.tabulate(10)(i => Tag(sourceDocumentSetId, "tag-" + i, None))
        Schema.tags.insert(tags)
        tagIdMapping = TagCloner.clone(sourceDocumentSetId, cloneDocumentSetId)

        sourceTags = Schema.tags.where(t => t.documentSetId === sourceDocumentSetId).toSeq
        cloneTags = Schema.tags.where(t => t.documentSetId === cloneDocumentSetId).toSeq

      }
    }

    "clone tags" in new TagContext {
      val cloneData = cloneTags.map(t => (t.name, t.color))
      val expectedTagData = sourceTags.map(t => (t.name, t.color))

      cloneData must haveTheSameElementsAs(expectedTagData)
    }

    "map source tag ids to clone tag ids" in new TagContext {
      val mappedIds = sourceTags.flatMap(t => tagIdMapping.get(t.id))
      
      mappedIds must haveTheSameElementsAs(cloneTags.map(_.id))
    }
  }

  step(shutdownDb)
}