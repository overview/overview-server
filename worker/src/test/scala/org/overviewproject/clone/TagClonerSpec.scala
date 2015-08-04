package com.overviewdocs.clone

import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.models.tables.Tags
import com.overviewdocs.test.DbSpecification

class TagClonerSpec extends DbSpecification {
  "TagCloner" should {

    trait TagContext extends DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet()
      val cloneDocumentSet = factory.documentSet()

      val sourceTags = Seq.tabulate(2)(i => factory.tag(documentSetId=sourceDocumentSet.id, name="tag-" + i))

      val tagIdMapping = DeprecatedDatabase.inTransaction {
        TagCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      }

      val cloneTags = blockingDatabase.seq(
        Tags
          .filter(_.documentSetId === cloneDocumentSet.id)
          .sortBy(_.name)
      )
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
}
