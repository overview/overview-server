package views.helper

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.i18n.{Lang,Messages}

import org.overviewproject.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState}
import test.helpers.MockMessagesApi

class DocumentSetHelperSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    implicit val messages = new Messages(Lang("en"), new MockMessagesApi())
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

      message must beEqualTo("views.ImportJob._documentSetCreationJob.jobs_to_process,2")
    }

    "show waiting when file upload job is started" in new BaseScope {
      job.state returns state.FilesUploaded
      job.statusDescription returns "file_processing_not_started"
      override val nAheadInQueue = 2

      message must beEqualTo("views.ImportJob._documentSetCreationJob.jobs_to_process,2")
    }

    "show processing when file upload job is extracting text" in new BaseScope {
      job.state returns state.TextExtractionInProgress
      job.statusDescription returns "processing_files:2:4"

      message must beEqualTo("views.ImportJob._documentSetCreationJob.job_state_description.processing_files,2,4")
    }

    "show retrieving when retrieving" in new BaseScope {
      job.state returns state.InProgress
      job.statusDescription returns "retrieving_documents:2:4"

      message must beEqualTo("views.ImportJob._documentSetCreationJob.job_state_description.retrieving_documents,2,4")
    }

    "show clustering when clustering" in new BaseScope {
      job.state returns state.InProgress
      job.statusDescription returns "clustering"

      message must beEqualTo("views.ImportJob._documentSetCreationJob.job_state_description.clustering")
    }
  }
}
