package controllers.api

import scala.concurrent.Future

import org.overviewproject.tree.orm.Tag

class TagControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockStorage = mock[TagController.Storage]
    val controller = new TagController {
      override val storage = mockStorage
    }
  }

  "index" should {
    trait IndexScope extends BaseScope {
      val documentSetId = 1L
      def tags: Seq[Tag] = Seq()
      mockStorage.index(1L) returns Future(tags) // async, so we get overrides
      override lazy val action = controller.index(documentSetId)
    }

    "return JSON with status code 200" in new IndexScope {
      status(result) must beEqualTo(OK)
      contentType(result) must beSome("application/json")
    }

    "return an empty Array when there are no tags" in new IndexScope {
      override def tags = Seq()
      contentAsString(result) must beEqualTo("[]")
    }

    "return some Tags when there are Tags" in new IndexScope {
      override def tags = Seq(
        Tag(documentSetId=documentSetId, name="foo", color="123456", id=1L),
        Tag(documentSetId=documentSetId, name="bar", color="234567", id=2L)
      )
      val json = contentAsString(result)
      json must /#(0) /("name" -> "foo")
      json must /#(0) /("color" -> "#123456")
      json must /#(1) /("name" -> "bar")
    }
  }
}
