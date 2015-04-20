package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import scala.concurrent.Future

import controllers.backend.{DocumentNodeBackend,SelectionBackend}
import controllers.backend.exceptions.SearchParseFailed
import models.{Selection,SelectionRequest}

class DocumentNodeControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockSelectionBackend = smartMock[SelectionBackend]
    val mockDocumentNodeBackend = smartMock[DocumentNodeBackend]
    val controller = new DocumentNodeController {
      override val documentNodeBackend = mockDocumentNodeBackend
      override val selectionBackend = mockSelectionBackend
    }
  }

  "#countByNode" should {
    trait CountByNodeScope extends BaseScope {
      val documentSetId = 123L
      val mockSelection = smartMock[Selection]
      mockSelectionBackend.findOrCreate(any, any) returns Future.successful(mockSelection)
      mockSelectionBackend.create(any, any) returns Future.successful(mockSelection)
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

    "grab selectionRequest from the HTTP post" in new CountByNodeScope {
      override val requestBody = Seq("countNodes" -> "1,2,3", "tags" -> "3")
      h.status(result)
      there was one(mockSelectionBackend)
        .findOrCreate(request.user.email, SelectionRequest(documentSetId, tagIds=Seq(3L)))
    }

    "use SelectionBackend.create() when refresh=true" in new CountByNodeScope {
      override val requestBody = Seq("countNodes" -> "1,2,3", "tags" -> "3", "refresh" -> "true")
      h.status(result)
      there was one(mockSelectionBackend)
        .create(request.user.email, SelectionRequest(documentSetId, tagIds=Seq(3L)))
    }

    "pass selectionRequest and nodes to documentNodeBackend" in new CountByNodeScope {
      override val requestBody = Seq("countNodes" -> "1,2,3", "tags" -> "3")
      h.status(result)
      there was one(mockDocumentNodeBackend).countByNode(mockSelection, Seq(1L, 2L, 3L))
    }

    "succeed if countNodes are not specified" in new CountByNodeScope {
      override val requestBody = Seq("tags" -> "3")
      h.status(result) must beEqualTo(h.OK)
      there was one(mockDocumentNodeBackend).countByNode(mockSelection, Seq())
    }

    "succeed if no selection is specified" in new CountByNodeScope {
      override val requestBody = Seq("countNodes" -> "1,2,3")
      h.status(result) must beEqualTo(h.OK)
      there was one(mockSelectionBackend)
        .findOrCreate(request.user.email, SelectionRequest(documentSetId))
    }

    "succeed if SearchParseFailed prevents us from creating a Selection" in new CountByNodeScope {
      mockSelectionBackend.findOrCreate(any, any) returns Future.failed(new SearchParseFailed("foo", new Exception()))
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("{}")
    }
  }
}
