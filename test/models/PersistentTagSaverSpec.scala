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

      def findTag(tagName: String): Option[Long] =
        SQL("SELECT id FROM tag WHERE name = {name}").
          on("name" -> tagName).as(scalar[Long] *).headOption

      def findTagColor(tagId: Long): Option[String] =
        SQL("SELECT color FROM tag WHERE id = {id}").on("id" -> tagId).
          as(get[Option[String]]("color") single)
    }

    trait TaggedDocument extends SavedTag {
      override def setupWithDb = {
        val documentId = insertDocument(documentSetId, "title", "dcId")
        tagDocuments(tagId.get, Seq(documentId))
      }
    }

    "add a new tag to the database, returning id" in new SavedTag {
      tagId must not beNone

      val foundId = findTag(name)
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

      val deletedTag = findTag(name)

      deletedTag must beNone
    }

    "rename tag" in new SavedTag {
      val newName = "new tag name"
      val color = "112358"

      tagSaver.update(tagId.get, newName, color)
      val notFound = findTag(name)

      notFound must beNone

      val renamed = findTag(newName)

      renamed must beSome.like { case id => id must be equalTo (tagId.get) }
    }

    "change tag color" in new SavedTag {
      val color = "abcdef"

      tagSaver.update(tagId.get, name, color)
      findTagColor(tagId.get) must beSome.like { case c => c must be equalTo (color) }
    }

  }

  step(stop)
}
