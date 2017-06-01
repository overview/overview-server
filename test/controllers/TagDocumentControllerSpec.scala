package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{TagDocumentBackend,SelectionBackend}
import models.{InMemorySelection,Selection}

class TagDocumentControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val selection = InMemorySelection(Seq(2L, 3L, 4L)) // override for a different Selection
    val mockTagDocumentBackend = smartMock[TagDocumentBackend]
    val mockSelectionBackend = smartMock[SelectionBackend]
    mockSelectionBackend.findOrCreate(any, any, any) returns Future(selection)
    val controller = new TagDocumentController(
      mockTagDocumentBackend,
      mockSelectionBackend
    )
  }

  "#count" should {
    trait CountScope extends BaseScope {
      val documentSetId = 1L
      val formBody: Seq[(String,String)] = Seq()
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.count(documentSetId)(request)

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

    "Use IDs from a Selection" in new CountScope {
      override val formBody = Seq("q" -> "moo")
      h.status(result)
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

      mockTagDocumentBackend.createMany(any, any) returns Future.successful(())
    }

    "return Created" in new CreateManyScope {
      h.status(result) must beEqualTo(h.CREATED)
    }

    "call tagDocumentBackend.createMany" in new CreateManyScope {
      h.status(result)
      there was one(mockTagDocumentBackend).createMany(tagId, Seq(2L, 3L, 4L))
    }
  }

  "#destroyMany" should {
    trait DestroyManyScope extends BaseScope {
      val documentSetId = 1L
      val tagId = 2L
      val formBody: Seq[(String,String)] = Seq()
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.destroyMany(documentSetId, tagId)(request)

      mockTagDocumentBackend.destroyMany(any, any) returns Future.successful(())
    }

    "return NoContent" in new DestroyManyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "call tagDocumentBackend.destroyMany" in new DestroyManyScope {
      h.status(result)
      there was one(mockTagDocumentBackend).destroyMany(tagId, Seq(2L, 3L, 4L))
    }
  }
}
