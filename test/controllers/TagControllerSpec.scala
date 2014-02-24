package controllers

import play.api.libs.json.Json
import org.specs2.specification.Scope

import org.overviewproject.tree.orm.Tag

class TagControllerSpec extends ControllerSpecification {
  class BaseScope extends Scope {
    val mockStorage = mock[TagController.Storage]
    val controller = new TagController {
      override val storage = mockStorage
    }
  }

  "indexJson" should {
    class IndexJsonScope extends BaseScope {
      val documentSetId = 1L
      def tagsWithCounts : Iterable[(Tag,Long)] = Seq()
      mockStorage.findTagsWithCounts(anyInt) answers { (_) => tagsWithCounts }
      val result = controller.indexJson(documentSetId)(fakeAuthorizedRequest)
    }

    "show an empty list" in new IndexJsonScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("""{"tags":[]}""")
    }

    "prevent caching of result" in new IndexJsonScope {
      h.header(h.CACHE_CONTROL, result) must beSome("max-age=0")
    }

    "show a full list" in new IndexJsonScope {
      override def tagsWithCounts = Seq(
        (Tag(documentSetId=documentSetId, id=1, name="tag1", color="111111") -> 5L),
        (Tag(documentSetId=documentSetId, id=2, name="tag2", color="222222") -> 10L)
      )
      h.status(result) must beEqualTo(h.OK)
      h.contentAsJson(result) must beEqualTo(Json.obj(
        "tags" -> Json.arr(
          Json.obj("id" -> 1, "name" -> "tag1", "color" -> "#111111", "size" -> 5),
          Json.obj("id" -> 2, "name" -> "tag2", "color" -> "#222222", "size" -> 10)
        )
      ))
    }
  }

  "indexJsonWithTree" should {
    class IndexJsonWithTreeScope extends BaseScope {
      val documentSetId = 1L
      val treeId = 2L
      def tagsWithCounts : Iterable[(Tag,Long,Long)] = Seq()
      mockStorage.findTagsWithCounts(anyInt, anyInt) answers { (_) => tagsWithCounts }
      val result = controller.indexJsonWithTree(documentSetId, treeId)(fakeAuthorizedRequest)
    }

    "show an empty list" in new IndexJsonWithTreeScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("""{"tags":[]}""")
    }

    "prevent caching of result" in new IndexJsonWithTreeScope {
      h.header(h.CACHE_CONTROL, result) must beSome("max-age=0")
    }

    "show a full list" in new IndexJsonWithTreeScope {
      override def tagsWithCounts = Seq(
        (Tag(documentSetId=documentSetId, id=1, name="tag1", color="111111"), 5L, 5L),
        (Tag(documentSetId=documentSetId, id=2, name="tag2", color="222222"), 10L, 8L)
      )
      h.status(result) must beEqualTo(h.OK)
      h.contentAsJson(result) must beEqualTo(Json.obj(
        "tags" -> Json.arr(
          Json.obj("id" -> 1, "name" -> "tag1", "color" -> "#111111", "size" -> 5, "sizeInTree" -> 5),
          Json.obj("id" -> 2, "name" -> "tag2", "color" -> "#222222", "size" -> 10, "sizeInTree" -> 8)
        )
      ))
    }
  }
}
