package models.orm

import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{start, stop}

class TagSpec extends Specification {

  step (start(FakeApplication()))

  "Tag" should {

    trait TagContext extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("TagSpec")
      val name = "some tag"
    }
    
    "be findable by name" in new TagContext {
      val tagId = insertTag(documentSetId, name)
      
      val tag = Tag.findByName(documentSetId, name)

      tag must beSome.like { case t: Tag =>
	t.id must be equalTo(tagId)
	t.documentSetId must be equalTo(documentSetId)
	t.name must be equalTo(name)
	t.color must beNone
      }
    }

    "be saveable" in new TagContext {
      val tag = Tag(documentSetId = documentSetId, name = name)
      tag.save

      tag.id must not be equalTo(0)
    }

  }

  step(stop)

}
