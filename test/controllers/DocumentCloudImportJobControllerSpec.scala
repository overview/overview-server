package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{DocumentCloudImport,DocumentSet}
import com.overviewdocs.test.factories.{PodoFactory=>factory}
import controllers.backend.DocumentSetBackend
import controllers.util.JobQueueSender

class DocumentCloudImportJobControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockJobQueueSender = smartMock[JobQueueSender]
    val mockStorage = smartMock[DocumentCloudImportJobController.Storage]
    val controller = new DocumentCloudImportJobController with TestController {
      override val documentSetBackend = mockDocumentSetBackend
      override val jobQueueSender = mockJobQueueSender
      override val storage = mockStorage
    }
  }

  trait CreateScope extends BaseScope {
    val anImport = factory.documentCloudImport()
    mockDocumentSetBackend.create(any, any) returns Future.successful(factory.documentSet(id=123L))
    mockStorage.insertImport(any) returns Future.successful(anImport)
    def formBody = Seq("title" -> "title", "query" -> "projectid:1-slug", "lang" -> "en")
    def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create()(request)
  }

  "DocumentCloudImportJobController" should {
    "submit a DocumentCloudImport" in new CreateScope {
      h.status(result) must beEqualTo(h.SEE_OTHER)
      there was one(mockDocumentSetBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          attributes.title must beEqualTo("title")
        },
        beLike[String] { case s => s must beEqualTo(request.user.email) }
      )

      val captor = capture[DocumentCloudImport.CreateAttributes]
      there was one(mockStorage).insertImport(captor)
      captor.value must beEqualTo(
        DocumentCloudImport.CreateAttributes(123L, "projectid:1-slug", "", "", false, "en")
          .copy(createdAt=captor.value.createdAt)
      )

      there was one(mockJobQueueSender).send(DocumentSetCommands.AddDocumentsFromDocumentCloud(anImport))
    }

    "redirect to /documentsets" in new CreateScope {
      h.redirectLocation(result) must beSome("/documentsets/123")
    }

    "not submit an invalid job" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      there was no(mockDocumentSetBackend).create(any, any)
    }
  }
}
