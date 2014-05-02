package views.helper

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, Step}
import play.api.Play.{start,stop}
import play.api.i18n.Lang
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState}

class DocumentSetHelperSpec extends Specification with Mockito {
  step(start(FakeApplication())) // to enable translation
  // Neater would be to use dependency injection on views.Magic.t()....

  trait BaseScope extends Scope {
    implicit val lang = Lang("en")
    val state = DocumentSetCreationJobState

    val job = mock[DocumentSetCreationJob]
    val nAheadInQueue = 0

    def message = DocumentSetHelper.jobDescriptionMessage(job, nAheadInQueue)
  }

  "DocumentSetHelper" should {
    "show waiting at the start" in new BaseScope {
      job.state returns state.NotStarted
      job.statusDescription returns "file_processing_not_started"
      override val nAheadInQueue = 2

      message must beEqualTo("Waiting for 2 jobs to complete before processing can begin")
    }

    "show waiting when file upload job is started" in new BaseScope {
      job.state returns state.FilesUploaded
      job.statusDescription returns "file_processing_not_started"
      override val nAheadInQueue = 2

      message must beEqualTo("Waiting for 2 jobs to complete before processing can begin")
    }

    "show processing when file upload job is extracting text" in new BaseScope {
      job.state returns state.TextExtractionInProgress
      job.statusDescription returns "processing_files:2:4"

      message must beEqualTo("Extracting text from file 2/4")
    }

    "show retrieving when retrieving" in new BaseScope {
      job.state returns state.InProgress
      job.statusDescription returns "retrieving_documents:2:4"

      message must beEqualTo("Reading file 2/4")
    }

    "show clustering when clustering" in new BaseScope {
      job.state returns state.InProgress
      job.statusDescription returns "clustering"

      message must beEqualTo("Clustering")
    }
  }

  step(stop)
}
