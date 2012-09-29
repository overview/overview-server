package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class PersistentTagSaverSpec extends Specification {

  step(start(FakeApplication()))

  "PersistentTagSaver" should {

    trait SavedTag extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("TagSaverSpec")
      lazy val name = "a tag"
      lazy val tagSaver = new PersistentTagSaver()
      lazy val tagId = tagSaver.save(documentSetId, name)

      def findTag: Option[Long] = SQL("SELECT id FROM tag WHERE name = {name}").
        on("name" -> name).as(scalar[Long] *).headOption

    }

    trait TaggedDocument extends SavedTag {
      override def setupWithDb = {
        val documentId = insertDocument(documentSetId, "title", "dcId")
        tagDocuments(tagId.get, Seq(documentId))
      }
    }

    "add a new tag to the database, returning id" in new SavedTag {
      tagId must not beNone

      val foundId = findTag
      tagId must be equalTo (foundId)
    }

    "return None if attempting to add already existing tag" in new SavedTag {
      tagId must beSome
      val noId = tagSaver.save(documentSetId, name)

      noId must beNone
    }

    "delete a tag" in new TaggedDocument {
      val rowsDeleted = tagSaver.delete(tagId.get)

      rowsDeleted must be equalTo (2)

      val deletedTag = findTag

      deletedTag must beNone
    }
  }

  step(stop)
}
