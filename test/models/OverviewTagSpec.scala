package models

import helpers.DbSetup._
import helpers.DbTestContext
import models.orm.Tag
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class OverviewTagSpec extends Specification {

  step(start(FakeApplication()))

  "OverviewTag" should {

    trait DocumentSetContext extends DbTestContext {
      val tagName = "name of tag"
      lazy val documentSetId = insertDocumentSet("OverviewTagSpec")
      val potentialTag = PotentialTag(tagName)
    }

    trait ExistingTag extends DocumentSetContext {
      var tagId: Long = _

      override def setupWithDb = tagId = insertTag(documentSetId, tagName)
    }

    trait LoadedTag extends ExistingTag {
      var tag: OverviewTag = _

      override def setupWithDb = {
        super.setupWithDb
        tag = PotentialTag(tagName).inDocumentSet(documentSetId).getOrElse(throw new Exception("Missing tag"))
      }
    }

    "be created from a PotentialTag" in new DocumentSetContext {
      val newTag: OverviewTag = potentialTag.create(documentSetId)

      newTag.id must not be equalTo(0)
    }

    "be loaded from document set if exists" in new ExistingTag {
      val foundTag: Option[OverviewTag] = potentialTag.inDocumentSet(documentSetId)

      foundTag must beSome.like { case t => t.id must be equalTo (tagId) }
    }

    "be loaded by id" in new ExistingTag {
      val foundTag = OverviewTag.findById(documentSetId, tagId)

      foundTag must beSome.like { case t => t.id must be equalTo(tagId) }
    }
    
    "load None if not in document set" in new DocumentSetContext {
      val foundTag: Option[OverviewTag] = potentialTag.inDocumentSet(documentSetId)

      foundTag must beNone
    }

    "be renameable" in new LoadedTag {
      val newName = "new name"

      val renamedTag: OverviewTag = tag.withName(newName)

      renamedTag.name must be equalTo (newName)
    }

    "allow color change" in new LoadedTag {
      val newColor = "d0bed0"
      val coloredTag: OverviewTag with TagColor = tag.withColor(newColor)

      coloredTag.color must be equalTo (newColor)
    }

    "return None if color is not set" in new LoadedTag {
      val coloredTag = tag.withColor

      coloredTag must beNone
    }

    "return tag with color if color is set" in new LoadedTag {
      val newColor = "d0bed0"
      val tagWithColor: OverviewTag = tag.withColor(newColor)
      val coloredTag = tagWithColor.withColor

      coloredTag must beSome.like { case t => t.color must be equalTo (newColor) }
    }

    "be saveable" in new LoadedTag {
      val newName = "new name"
      tag.withName(newName).save

      val renamedTag = PotentialTag(newName).inDocumentSet(documentSetId)
      renamedTag must beSome.like { case t => t.id must be equalTo tag.id }
    }

    "be deleteable" in new LoadedTag {
      tag.delete

      val deletedTag = PotentialTag(tagName).inDocumentSet(documentSetId)

      deletedTag must beNone
    }
  }

  step(stop)
}
