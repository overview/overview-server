package models.orm

import helpers.DbSetup._
import helpers.DbTestContext
import org.postgresql.util.PSQLException
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

    trait ExistingTag extends TagContext {
      lazy val tag = Tag(documentSetId = documentSetId, name = name)
    }

    trait SavedTag extends ExistingTag {
      override def setupWithDb = tag.save
    }
    
    trait DocumentsTagged extends SavedTag {
      override def setupWithDb = {
	super.setupWithDb
	val documentId = insertDocument(documentSetId, "title", "dcId")
	tagDocuments(tag.id, Seq(documentId))
      }
    }
    
    trait TagForUpdate extends ExistingTag {
      val newName = "some other tag now"
      val newColor = "12adff"
    }
    
    "be findable by id" in new TagContext {
      val tagId = insertTag(documentSetId, name)

      val tag = Tag.findById(documentSetId, tagId)

      tag must beSome.like { case t: Tag =>
	t.id must be equalTo(tagId)
	t.documentSetId must be equalTo(documentSetId)
	t.name must be equalTo(name)
	t.color must beNone
      }
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

    "be saveable" in new ExistingTag {
      tag.save
      tag.id must not be equalTo(0)
    }

    "be updateable" in new TagForUpdate {
      val updatedTag = tag.withUpdate(newName, newColor)

      updatedTag.name must be equalTo(newName)
      updatedTag.color must beSome.like { case c => c must be equalTo(newColor) }
    }

    "be saveable after update" in new TagForUpdate {
      tag.save
      val tagId = tag.id
      
      val updatedTag = tag.withUpdate(newName, newColor)

      updatedTag.save
      val storedTag = Tag.findByName(documentSetId, newName)
      storedTag must beSome.like { case t: Tag =>
	t.id must be equalTo(tagId)
	t.documentSetId must be equalTo(documentSetId)
	t.name must be equalTo(newName)
	t.color must beSome.like { case c => c must be equalTo(newColor) }
      }
      
    }

    "be deleteable" in new ExistingTag {
      tag.save
      tag.delete

      val deletedTag = Tag.findByName(documentSetId, name)
    
      deletedTag must beNone
    }

    "be deletable when documents are tagged" in new DocumentsTagged {
      tag.delete must not(throwA[java.lang.RuntimeException])

      val deletedTag = Tag.findByName(documentSetId, name)
      deletedTag must beNone
    }
  }
    

  step(stop)

}
