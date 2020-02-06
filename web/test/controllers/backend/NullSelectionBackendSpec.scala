package controllers.backend

import java.util.Date
import org.mockito.ArgumentMatchers
import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.query.Field
import com.overviewdocs.searchindex.SearchWarning
import models.{InMemorySelection,Selection,SelectionRequest,SelectionWarning}

class NullSelectionBackendSpec extends NullBackendSpecification with Mockito {
  trait BaseScope extends NullScope {
    def resultIds: Vector[Long] = Vector.empty
    def warnings: List[SelectionWarning] = Nil
    val dsBackend = mock[DocumentSelectionBackend]
    val backend = new NullSelectionBackend(dsBackend)
    dsBackend.createSelection(any[SelectionRequest], any[Double => Unit]) returns Future.successful(InMemorySelection(resultIds, warnings))

    val userEmail: String = "user@example.org"
    val documentSetId: Long = 1L
  }

  "NullSelectionBackend" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        lazy val request = SelectionRequest(documentSetId)
        def create = await(backend.create(userEmail, request, _ => ()))
        lazy val result = create
      }

      "return a Selection with the returned document IDs" in new CreateScope {
        override def resultIds = Vector(1L, 2L, 3L)
        await(result.getAllDocumentIds) must beEqualTo(Vector(1L, 2L, 3L))
      }

      "return warnings" in new CreateScope {
        override def warnings = SelectionWarning.SearchIndexWarning(SearchWarning.TooManyExpansions(Field.Text, "foo", 2)) :: Nil
        result.warnings must beEqualTo(warnings)
      }

      "return a different Selection each time" in new CreateScope {
        create
        create
        there were two(dsBackend).createSelection(any, any)
      }

      "pass the SelectionRequest to the dsBackend" in new CreateScope {
        create
        there was one(dsBackend).createSelection(ArgumentMatchers.eq(request), any[Double => Unit])
      }

      "pass a failure back" in new CreateScope {
        val t = new Throwable("moo")
        dsBackend.createSelection(any[SelectionRequest], any[Double => Unit]) returns Future.failed(t)
        create must throwA[Throwable](message="moo")
      }
    }
  }
}
