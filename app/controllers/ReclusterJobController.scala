package controllers

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.DocumentSetCreationJobState
import com.overviewdocs.models.tables.DocumentSetCreationJobs
import controllers.auth.Authorities.userOwningJob
import controllers.auth.AuthorizedAction
import controllers.util.JobQueueSender

trait ReclusterJobController extends Controller {
  val storage : ReclusterJobController.Storage
  val jobQueue: JobQueueSender

  def delete(jobId: Long) = AuthorizedAction(userOwningJob(jobId)).async {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      maybeDocumentSetId <- storage.markJobCancelledAndGetDocumentSetId(jobId)
    } yield {
      maybeDocumentSetId.map { documentSetId =>
        jobQueue.send(DocumentSetCommands.CancelJob(documentSetId, jobId))
      }
      NoContent
    }
  }
}

object ReclusterJobController extends ReclusterJobController {
  trait Storage {
    def markJobCancelledAndGetDocumentSetId(jobId: Long): Future[Option[Long]]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    lazy val getDocumentSetId = Compiled { (jobId: Rep[Long]) =>
      DocumentSetCreationJobs
        .filter(_.id === jobId)
        .map(_.documentSetId)
    }

    lazy val updater = Compiled { (jobId: Rep[Long]) =>
      DocumentSetCreationJobs
        .filter(_.id === jobId)
        .map(_.state)
    }

    override def markJobCancelledAndGetDocumentSetId(jobId: Long): Future[Option[Long]] = {
      import database.executionContext
      // Slick has no UPDATE ... RETURNING, so we do two queries. Races won't matter.
      for {
        id <- database.option(getDocumentSetId(jobId))
        _ <- database.runUnit(updater(jobId).update(DocumentSetCreationJobState.Cancelled)) // even if the row isn't there
      } yield id
    }
  }

  override val storage = DatabaseStorage
  override val jobQueue = JobQueueSender
}
