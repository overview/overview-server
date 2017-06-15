package controllers.api

import scala.concurrent.Future

import controllers.backend.TagBackend
import com.overviewdocs.models.Tag

class TagControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[TagBackend]
    val controller = new TagController(mockBackend)
  }

  "index" should {
    trait IndexScope extends BaseScope {
      val documentSetId = 1L
      def tags: Seq[Tag] = Seq()
      mockBackend.index(1L) returns Future(tags) // async, so we get overrides
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
        factory.tag(id=1L, name="foo", color="123456"),
        factory.tag(id=2L, name="bar", color="234567")
      )
      val json = contentAsString(result)
      json must /#(0) /("name" -> "foo")
      json must /#(0) /("color" -> "#123456")
      json must /#(1) /("name" -> "bar")
    }
  }
}
