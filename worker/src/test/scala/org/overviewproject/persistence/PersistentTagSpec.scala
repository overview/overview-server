package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ DocumentSet, Tag }

class PersistentTagSpec extends DbSpecification {

  step(setupDb)

  "PersisentTag" should {

    trait TagContext extends DbTestContext {
      val tagName = "tag1"

      var documentSetId: Long = _

      override def setupWithDb = {
        documentSetId = Schema.documentSets.insert(DocumentSet())
      }
    }

    "create a non-existent tag" in new TagContext {
      val tag = PersistentTag.findOrCreate(documentSetId, tagName)

      Schema.tags.get(tag.id) must be equalTo (tag)
    }

    "find an existing tag" in new TagContext {
      val tag = Tag(documentSetId, tagName, "ffffff")
      Schema.tags.insertOrUpdate(tag)

      val foundTag = PersistentTag.findOrCreate(documentSetId, tagName)
      
      foundTag must be equalTo(tag)
    }
  }

  step(shutdownDb)
}