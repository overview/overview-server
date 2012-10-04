package models

import org.specs2.mock._
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PersistentTagSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  "PersistentTag" should {

    trait MockComponents extends Scope {
      val loader = mock[PersistentTagLoader]
      val parser = mock[DocumentListParser]
      val saver = mock[PersistentTagSaver]
      val documentSetId = 4l
      val dummyTagId = 23l
      val name = "a tag"
    }

    trait NoTag extends MockComponents with Before {
      def before = loader loadByName (documentSetId, name) returns None
    }

    trait ExistingTag extends MockComponents with Before {
      def before = loader loadByName (documentSetId, name) returns Some(dummyTagId)
    }

    trait DocumentsTagged extends MockComponents with Before {
      val documentIds = Seq(1l, 2l)
      val tag = core.Tag(dummyTagId, name, None, core.DocumentIdList(documentIds, 3))

      def before = loader loadByName(documentSetId, name) returns Some(dummyTagId)
    }

    "be created by findOrCreateByName factory method if not in database" in new NoTag {

      saver save (documentSetId, name) returns Some(dummyTagId)

      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      there was one(loader).loadByName(documentSetId, name)
      there was one(saver).save(documentSetId, name)

      tag.id must be equalTo (dummyTagId)
    }

    "be loaded by findOrCreateByName factory method if in database" in new ExistingTag {
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      there was one(loader).loadByName(documentSetId, name)
      there was no(saver).save(documentSetId, name)

      tag.id must be equalTo (dummyTagId)

    }

    "be loaded by findByName if in database" in new ExistingTag {
      val tag = PersistentTag.findByName(name, documentSetId, loader, parser, saver)

      tag must beSome
      tag.get.id must be equalTo (dummyTagId)
    }

    "return None from findByName if tag is not in database" in new NoTag {
      val tag = PersistentTag.findByName(name, documentSetId, loader, parser, saver)

      tag must beNone
    }

    "should ask loader for number of documents with tag" in new ExistingTag {
      val dummyCount = 454
      loader countDocuments (dummyTagId) returns dummyCount

      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      val count = tag.count

      there was one(loader).countDocuments(dummyTagId)

      count must be equalTo (dummyCount)
    }

    "should ask loader for number of documents with tag per node" in new ExistingTag {
      val dummyCounts = Seq((1l, 5l), (2l, 3345l))
      val nodeIds = Seq(1l, 2l)

      loader countsPerNode (nodeIds, dummyTagId) returns dummyCounts

      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      val counts = tag.countsPerNode(nodeIds)

      there was one(loader).countsPerNode(nodeIds, dummyTagId)

      counts must be equalTo (dummyCounts)
    }

    "ask loader and parser to create tag" in new ExistingTag {

      val tagData = Seq((dummyTagId, name, 0l, None, None))
      val dummyTag = core.Tag(dummyTagId, name, None, core.DocumentIdList(Nil, 0))

      loader loadTag (dummyTagId) returns tagData
      parser createTags (tagData) returns Seq(dummyTag)

      val persistentTag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      val tag = persistentTag.loadTag

      there was one(loader).loadTag(dummyTagId)
      there was one(parser).createTags(tagData)

      tag must be equalTo (dummyTag)
    }

    "load documents referenced by tag" in new DocumentsTagged {
      val persistentTag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      val documents = persistentTag.loadDocuments(tag)

      there was one(loader).loadDocuments(documentIds)
    }

    "delete the tag" in new ExistingTag {
      val rowsDeleted = 10
      saver delete (dummyTagId) returns rowsDeleted

      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      val count = tag.delete()

      count must be equalTo (rowsDeleted)

      there was one(saver).delete(dummyTagId)
    }

    "update the tag" in new ExistingTag {
      val newName = "new name"
      val newColor = "new color"

      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)

      tag.update(newName, newColor)

      there was one(saver).update(dummyTagId, newName, newColor)
    }
  }
}

