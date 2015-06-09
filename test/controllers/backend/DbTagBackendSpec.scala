package controllers.backend

import org.overviewproject.models.Tag
import org.overviewproject.models.tables.{DocumentTags,Tags}

class DbTagBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    import databaseApi._

    val backend = new DbTagBackend with org.overviewproject.database.DatabaseProvider

    def findTag(id: Long) = {
      blockingDatabase.option(Tags.filter(_.id === id))
    }

    def findDocumentTag(documentId: Long, tagId: Long) = {
      blockingDatabase.option(
        DocumentTags
          .filter(_.documentId === documentId)
          .filter(_.tagId === tagId)
      )
    }
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      val documentSet = factory.documentSet()
      lazy val result: Seq[Tag] = await(backend.index(documentSet.id))
    }

    "return an empty Seq" in new IndexScope {
      result must beEqualTo(Seq())
    }

    "return a Seq with Tags" in new IndexScope {
      val tag1 = factory.tag(documentSetId=documentSet.id)
      val tag2 = factory.tag(documentSetId=documentSet.id)

      result must containTheSameElementsAs(Seq(tag1, tag2))
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)

      val documentSetId = documentSet.id
      val tagId = tag.id
      lazy val result: Option[Tag] = await(backend.show(documentSetId, tagId))
    }

    "filter by DocumentSet ID" in new ShowScope {
      override val documentSetId = documentSet.id + 1
      result must beNone
    }

    "filter by Tag ID" in new ShowScope {
      override val tagId = tag.id + 1
      result must beNone
    }

    "return a Tag" in new ShowScope {
      result must beSome(tag)
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val documentSet = factory.documentSet()
      val attributes: Tag.CreateAttributes
      lazy val result: Tag = await(backend.create(documentSet.id, attributes))
    }

    "return a tag" in new CreateScope {
      override val attributes = Tag.CreateAttributes("foo", "123abc")
      result.documentSetId must beEqualTo(documentSet.id)
      result.name must beEqualTo("foo")
      result.color must beEqualTo("123abc")
    }

    "create the tag in the database" in new CreateScope {
      override val attributes = Tag.CreateAttributes("foo", "123abc")
      val dbResult = findTag(result.id)
      dbResult.map(_.documentSetId) must beSome(documentSet.id)
      dbResult.map(_.name) must beSome("foo")
      dbResult.map(_.color) must beSome("123abc")
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id, name="foo", color="123abc")
      val documentSetId: Long = documentSet.id
      val tagId: Long = tag.id
      val attributes: Tag.UpdateAttributes = Tag.UpdateAttributes("bar", "abc123")
      lazy val result: Option[Tag] = await(backend.update(documentSetId, tagId, attributes))
    }

    "update the Tag in the database" in new UpdateScope {
      result
      val dbResult = findTag(tag.id)
      dbResult.map(_.documentSetId) must beSome(documentSet.id)
      dbResult.map(_.name) must beSome("bar")
      dbResult.map(_.color) must beSome("abc123")
    }

    "return the updated Tag" in new UpdateScope {
      result.map(_.documentSetId) must beSome(documentSet.id)
      result.map(_.name) must beSome("bar")
      result.map(_.color) must beSome("abc123")
    }

    "return None when tagId does not point at a Tag" in new UpdateScope {
      override val tagId = tag.id + 1
      result must beNone
    }

    "return None when documentSetId is wrong" in new UpdateScope {
      override val documentSetId = documentSet.id + 1
      result must beNone
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)

      val documentSetId = documentSet.id
      val tagId = tag.id

      def destroy = await(backend.destroy(documentSetId, tagId))
    }

    "destroy the Tag" in new DestroyScope {
      destroy
      findTag(tag.id) must beNone
    }

    "filter by Tag" in new DestroyScope {
      val tag2 = factory.tag(documentSetId=documentSet.id)
      destroy
      findTag(tag2.id) must beSome
    }

    "filter by DocumentSet" in new DestroyScope {
      override val documentSetId = documentSet.id + 1
      destroy
      findTag(tag.id) must beSome
    }

    "destroy associated DocumentTags" in new DestroyScope {
      val doc = factory.document(documentSetId=documentSet.id)
      val dt = factory.documentTag(documentId=doc.id, tagId=tag.id)

      destroy

      findDocumentTag(dt.documentId, dt.tagId) must beNone
    }

    "not destroy other DocumentTags" in new DestroyScope {
      val doc = factory.document(documentSetId=documentSet.id)
      val tag2 = factory.tag(documentSetId=documentSet.id)
      val dt = factory.documentTag(documentId=doc.id, tagId=tag2.id)

      destroy

      findDocumentTag(dt.documentId, dt.tagId) must beSome
    }
  }
}
