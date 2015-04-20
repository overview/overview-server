package controllers.backend

import java.util.Date
import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{Selection,SelectionRequest}

class NullSelectionBackendSpec extends NullBackendSpecification with Mockito {
  trait BaseScope extends NullScope {
    def resultIds: Seq[Long] = Seq()
    val finder = mock[(SelectionRequest) => Future[Seq[Long]]]
    val backend = new NullSelectionBackend {
      override def findDocumentIds(request: SelectionRequest) = finder(request)
    }
    finder.apply(any[SelectionRequest]) returns Future(resultIds)
  }

  "NullSelectionBackend" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        val userEmail: String = "user@example.org"
        val documentSetId: Long = 1L
        val nodeIds: Seq[Long] = Seq()
        val tagIds: Seq[Long] = Seq()
        val documentIds: Seq[Long] = Seq()
        val storeObjectIds: Seq[Long] = Seq()
        val tagged: Option[Boolean] = None
        val q: String = ""

        lazy val request = SelectionRequest(documentSetId, nodeIds, tagIds, documentIds, storeObjectIds, tagged, q)
        def create = await(backend.create(userEmail, request))
        lazy val result = create
      }

      "return a Selection with the returned document IDs" in new CreateScope {
        override def resultIds = Seq(1L, 2L, 3L)
        result.getAllDocumentIds must beEqualTo(Seq(1L, 2L, 3L)).await
      }

      "return a different Selection each time" in new CreateScope {
        create.id must not(beEqualTo(create.id))
      }

      "pass the SelectionRequest to the finder" in new CreateScope {
        create
        there was one(finder).apply(request)
      }

      "pass a failure back" in new CreateScope {
        val t = new Throwable("moo")
        finder.apply(any[SelectionRequest]) returns Future.failed(t)
        create must throwA[Throwable](message="moo")
      }
    }
  }
}
