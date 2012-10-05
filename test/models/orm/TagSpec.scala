package models.orm

import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{start, stop}

class TagSpec extends Specification {

  step (start(FakeApplication()))

  "Tag" should {

    "be findable by name" in new DbTestContext {
      val documentSetId = insertDocumentSet("TagSpec")
      val name = "some tag"
      val tagId = insertTag(documentSetId, name)
      
      val tag = Tag.findByName(documentSetId, name)

      tag must beSome.like { case t: Tag =>
	t.id must be equalTo(tagId)
	t.documentSetId must be equalTo(documentSetId)
	t.name must be equalTo(name)
	t.color must beNone
      }

    }
  }

  step(stop)

}
