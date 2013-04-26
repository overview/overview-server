package org.overviewproject.persistence

import org.overviewproject.persistence.orm.{Schema, Tag}
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification

class PersistentTagSpec extends DbSpecification {

  step(setupDb)

  "PersisentTag" should {

    trait TagContext extends DbTestContext {
      val tagName = "tag1"

      var documentSetId: Long = _

      override def setupWithDb = {
        documentSetId = insertDocumentSet("PersistentTagSpec")
      }
    }

    "create a non-existent tag" in new TagContext {
      val tag = PersistentTag.findOrCreate(documentSetId, tagName)

      Schema.tags.get(tag.id) must be equalTo (tag)
    }

    "find an existing tag" in new TagContext {
      val tag = Tag(documentSetId, tagName)
      Schema.tags.insertOrUpdate(tag)

      val foundTag = PersistentTag.findOrCreate(documentSetId, tagName)
      
      foundTag must be equalTo(tag)
    }
  }

  step(shutdownDb)
}