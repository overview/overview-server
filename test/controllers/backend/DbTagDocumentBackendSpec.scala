package controllers.backend

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

class DbTagDocumentBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbTagDocumentBackend with org.overviewproject.database.DatabaseProvider

    def findDocumentTag(documentId: Long, tagId: Long) = {
      import databaseApi._
      blockingDatabase.option(
        DocumentTags
          .filter(_.documentId === documentId)
          .filter(_.tagId === tagId)
      )
    }
  }

  "#count" should {
    trait CountScope extends BaseScope {
      val documentSet = factory.documentSet()
      val doc1 = factory.document(documentSetId=documentSet.id)
      val doc2 = factory.document(documentSetId=documentSet.id)
      val doc3 = factory.document(documentSetId=documentSet.id)

      val tag1 = factory.tag(documentSetId=documentSet.id)
      factory.documentTag(doc1.id, tag1.id)

      val tag2 = factory.tag(documentSetId=documentSet.id)
      factory.documentTag(doc1.id, tag2.id)
      factory.documentTag(doc2.id, tag2.id)

      val tag3 = factory.tag(documentSetId=documentSet.id)
    }

    "return no counts when given no documents" in new CountScope {
      await(backend.count(documentSet.id, Seq())) must beEqualTo(Map())
    }

    "return no counts when documents have no tags" in new CountScope {
      await(backend.count(documentSet.id, Seq(doc3.id))) must beEqualTo(Map())
    }

    "filter out unselected Documents" in new CountScope {
      await(backend.count(documentSet.id, Seq(doc1.id, doc3.id))) must beEqualTo(Map(tag1.id -> 1, tag2.id -> 1))
    }

    "return proper counts" in new CountScope {
      await(backend.count(documentSet.id, Seq(doc1.id, doc2.id, doc3.id))) must beEqualTo(Map(tag1.id -> 1, tag2.id -> 2))
    }
  }

  "#createMany" should {
    trait CreateManyScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)
      val doc1 = factory.document(documentSetId=documentSet.id)
      val doc2 = factory.document(documentSetId=documentSet.id)
      val doc3 = factory.document(documentSetId=documentSet.id)
    }

    "tag the requested documents" in new CreateManyScope {
      await(backend.createMany(tag.id, Seq(doc1.id, doc2.id)))
      findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
      findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
      findDocumentTag(doc3.id, tag.id) must beNone
    }

    "work when double-tagging" in new CreateManyScope {
      await(backend.createMany(tag.id, Seq(doc1.id)))
      await(backend.createMany(tag.id, Seq(doc1.id, doc2.id)))
      findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
      findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
      findDocumentTag(doc3.id, tag.id) must beNone
    }

    "tag nothing" in new CreateManyScope {
      await(backend.createMany(tag.id, Seq()))
      findDocumentTag(doc1.id, tag.id) must beNone
      findDocumentTag(doc2.id, tag.id) must beNone
      findDocumentTag(doc3.id, tag.id) must beNone
    }
  }

  "#destroyMany" should {
    trait DestroyManyScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)
      val doc1 = factory.document(documentSetId=documentSet.id)
      val doc2 = factory.document(documentSetId=documentSet.id)
      val doc3 = factory.document(documentSetId=documentSet.id)
      val dt1 = factory.documentTag(doc1.id, tag.id)
      val dt2 = factory.documentTag(doc2.id, tag.id)
    }

    "untag the requested documents" in new DestroyManyScope {
      factory.documentTag(doc3.id, tag.id)
      await(backend.destroyMany(tag.id, Seq(doc1.id, doc2.id)))
      findDocumentTag(doc1.id, tag.id) must beNone
      findDocumentTag(doc2.id, tag.id) must beNone
      findDocumentTag(doc3.id, tag.id) must beSome(DocumentTag(doc3.id, tag.id))
    }

    "ignore already-untagged documents" in new DestroyManyScope {
      await(backend.destroyMany(tag.id, Seq(doc1.id, doc3.id)))
      findDocumentTag(doc1.id, tag.id) must beNone
      findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
      findDocumentTag(doc3.id, tag.id) must beNone
    }

    "ignore DocumentTags from other tags" in new DestroyManyScope {
      val tag2 = factory.tag(documentSetId=documentSet.id)
      factory.documentTag(doc1.id, tag2.id)
      await(backend.destroyMany(tag.id, Seq(doc1.id)))
      findDocumentTag(doc1.id, tag2.id) must beSome(DocumentTag(doc1.id, tag2.id))
    }

    "untag zero documents" in new DestroyManyScope {
      await(backend.destroyMany(tag.id, Seq()))
      findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
      findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
      findDocumentTag(doc3.id, tag.id) must beNone
    }
  }

  "#destroyAll" should {
    trait DestroyAllScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)
      val doc1 = factory.document(documentSetId=documentSet.id)
      val doc2 = factory.document(documentSetId=documentSet.id)
      val doc3 = factory.document(documentSetId=documentSet.id)
      val dt1 = factory.documentTag(doc1.id, tag.id)
      val dt2 = factory.documentTag(doc2.id, tag.id)
    }

    "destroy all document tags" in new DestroyAllScope {
      await(backend.destroyAll(tag.id))
      findDocumentTag(doc1.id, tag.id) must beNone
      findDocumentTag(doc2.id, tag.id) must beNone
      findDocumentTag(doc3.id, tag.id) must beNone
    }

    "ignore DocumentTags from other tags" in new DestroyAllScope {
      val tag2 = factory.tag(documentSetId=documentSet.id)
      factory.documentTag(doc1.id, tag2.id)
      await(backend.destroyAll(tag.id))
      findDocumentTag(doc1.id, tag2.id) must beSome(DocumentTag(doc1.id, tag2.id))
    }
  }
}
