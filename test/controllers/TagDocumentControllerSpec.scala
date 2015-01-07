package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.backend.{SelectionBackend,TagDocumentBackend}
import models.{SelectionLike,SelectionRequest}

class TagDocumentControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockSelectionBackend = smartMock[SelectionBackend]
    val mockTagDocumentBackend = smartMock[TagDocumentBackend]
    val controller = new TagDocumentController {
      override val selectionBackend = mockSelectionBackend
      override val tagDocumentBackend = mockTagDocumentBackend
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
