package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.backend.{SelectionBackend,TagDocumentBackend}
import models.{SelectionLike,SelectionRequest}

class TagDocumentControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockSelectionBackend = smartMock[SelectionBackend]
    val mockTagDocumentBackend = smartMock[TagDocumentBackend]
    val controller = new TagDocumentController {
      override val selectionBackend = mockSelectionBackend
      override val tagDocumentBackend = mockTagDocumentBackend
    }
  }

  "#count" should {
    trait CountScope extends BaseScope {
      val documentSetId = 1L
      val formBody: Seq[(String,String)] = Seq()
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.count(documentSetId)(request)

      val mockSelection = mock[SelectionLike]
      mockSelection.getAllDocumentIds returns Future.successful(Seq(2L, 3L, 4L))
      mockSelectionBackend.findOrCreate(any, any) returns Future.successful(mockSelection)
      mockTagDocumentBackend.count(any, any) returns Future.successful(Map())
    }

    "return 200 Ok with empty JSON" in new CountScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("{}")
    }

    "return JSON" in new CountScope {
      mockTagDocumentBackend.count(any, any) returns Future.successful(Map(5L -> 6, 7L -> 8))
      val json = h.contentAsString(result)
      json must /("5" -> 6)
      json must /("7" -> 8)
    }

    "findOrCreate a Selection and use its IDs" in new CountScope {
      override val formBody = Seq("q" -> "moo")
      h.status(result)
      there was one(mockSelectionBackend).findOrCreate(request.user.email, SelectionRequest(documentSetId, q="moo"))
      there was one(mockSelection).getAllDocumentIds
      there was one(mockTagDocumentBackend).count(documentSetId, Seq(2L, 3L, 4L))
    }
  }

  "#createMany" should {
    trait CreateManyScope extends BaseScope {
      val documentSetId = 1L
      val tagId = 2L
      val formBody: Seq[(String,String)] = Seq()
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.createMany(documentSetId, tagId)(request)

      val mockSelection = mock[SelectionLike]
      mockSelection.getAllDocumentIds returns Future.successful(Seq(3L, 4L, 5L))
      mockSelectionBackend.findOrCreate(any, any) returns Future.successful(mockSelection)
      mockTagDocumentBackend.createMany(any, any) returns Future.successful(())
    }

    "return Created" in new CreateManyScope {
      h.status(result) must beEqualTo(h.CREATED)
    }

    "findOrCreate a Selection" in new CreateManyScope {
      override val formBody = Seq("q" -> "moo")
      h.status(result)
      there was one(mockSelectionBackend).findOrCreate(request.user.email, SelectionRequest(documentSetId, q="moo"))
      there was one(mockSelection).getAllDocumentIds
    }

    "call tagDocumentBackend.createMany" in new CreateManyScope {
      h.status(result)
      there was one(mockTagDocumentBackend).createMany(tagId, Seq(3L, 4L, 5L))
    }
  }

  "#destroyMany" should {
    trait DestroyManyScope extends BaseScope {
      val documentSetId = 1L
      val tagId = 2L
      val formBody: Seq[(String,String)] = Seq()
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.destroyMany(documentSetId, tagId)(request)

      val mockSelection = mock[SelectionLike]
      mockSelection.getAllDocumentIds returns Future.successful(Seq(3L, 4L, 5L))
      mockSelectionBackend.findOrCreate(any, any) returns Future.successful(mockSelection)
      mockTagDocumentBackend.destroyMany(any, any) returns Future.successful(())
    }

    "return NoContent" in new DestroyManyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "findOrCreate a Selection" in new DestroyManyScope {
      override val formBody = Seq("q" -> "moo")
      h.status(result)
      there was one(mockSelectionBackend).findOrCreate(request.user.email, SelectionRequest(documentSetId, q="moo"))
      there was one(mockSelection).getAllDocumentIds
    }

    "call tagDocumentBackend.destroyMany" in new DestroyManyScope {
      h.status(result)
      there was one(mockTagDocumentBackend).destroyMany(tagId, Seq(3L, 4L, 5L))
    }
  }
}
