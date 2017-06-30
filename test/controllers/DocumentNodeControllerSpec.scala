package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{DocumentNodeBackend,SelectionBackend}
import models.InMemorySelection

class DocumentNodeControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val selection = InMemorySelection(Array(2L, 3L, 4L)) // override for a different Selection
    val mockDocumentNodeBackend = smartMock[DocumentNodeBackend]
    val mockSelectionBackend = smartMock[SelectionBackend]
    // We assume this controller doesn't care about onProgress because the user
    // recently cached a Selection. That's not necessarily true, but it should
    // hold true most of the time.
    mockSelectionBackend.findOrCreate(any, any, any, any) returns Future { selection }

    val controller = new DocumentNodeController(
      mockDocumentNodeBackend,
      mockSelectionBackend,
      testMessagesApi
    )
  }

  "#countByNode" should {
    trait CountByNodeScope extends BaseScope {
      val documentSetId = 123L
      mockDocumentNodeBackend.countByNode(any, any) returns Future.successful(Map())

      val requestBody: Seq[(String,String)] = Seq("countNodes" -> "1,2,3", "tags" -> "3")

      lazy val request = fakeAuthorizedRequest("POST", "/count").withFormUrlEncodedBody(requestBody: _*)
      lazy val result = controller.countByNode(documentSetId)(request)
    }

    "return counts as a JsObject" in new CountByNodeScope {
      mockDocumentNodeBackend.countByNode(any, any) returns Future.successful(Map(1L -> 2, 3L -> 4))
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")

      val json = h.contentAsString(result)
      json must /("1" -> 2)
      json must /("3" -> 4)
    }

    "pass Selection and nodes to documentNodeBackend" in new CountByNodeScope {
      override val requestBody = Seq("countNodes" -> "1,2,3", "tags" -> "3")
      h.status(result)
      there was one(mockDocumentNodeBackend).countByNode(selection, Seq(1L, 2L, 3L))
    }

    "succeed if countNodes are not specified" in new CountByNodeScope {
      override val requestBody = Seq("tags" -> "3")
      h.status(result) must beEqualTo(h.OK)
      there was one(mockDocumentNodeBackend).countByNode(selection, Seq())
    }
  }
}
