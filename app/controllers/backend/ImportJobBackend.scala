package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.tables.{DocumentSetCreationJobs,DocumentSetUsers,DocumentSets}
import org.overviewproject.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType}

trait ImportJobBackend extends Backend {
  /** Returns a list of ImportJob IDs, from currently processing to last in the
    * queue.
    */
  def indexIdsInProcessingOrder: Future[Seq[Long]]

  /** Returns a list of ImportJobs for the given user, in processing order.
    */
  def indexWithDocumentSets(userEmail: String): Future[Seq[(DocumentSetCreationJob,DocumentSet)]]

  /** Returns a list of ImportJobs for the given DocumentSet, in processing
    * order.
    */
  def index(documentSetId: Long): Future[Seq[DocumentSetCreationJob]]
}

trait DbImportJobBackend extends ImportJobBackend with DbBackend {
  import databaseApi._

  private lazy val processingIdsCompiled = Compiled {
    DocumentSetCreationJobs
      .filter(_.state =!= DocumentSetCreationJobState.Error)
      .filter(_.state =!= DocumentSetCreationJobState.Cancelled)
      .filter(_.jobType =!= DocumentSetCreationJobType.Recluster)
      .sortBy(_.id)
      .map(_.id)
  }

  private lazy val indexWithDocumentSetsCompiled = Compiled { userEmail: Rep[String] =>
    val allowedDocumentSetIds = DocumentSetUsers
      .filter(_.userEmail === userEmail)
      .map(_.documentSetId)

    for {
      job <- DocumentSetCreationJobs
        .filter(_.documentSetId in allowedDocumentSetIds)
        .filter(_.jobType =!= DocumentSetCreationJobType.Recluster)
        .filter(_.state =!= DocumentSetCreationJobState.Cancelled)
        .sortBy(_.id.desc)
      documentSet <- DocumentSets.filter(_.id === job.documentSetId)
    } yield (job, documentSet)
  }

  private lazy val indexCompiled = Compiled { documentSetId: Rep[Long] =>
    DocumentSetCreationJobs
      .filter(_.documentSetId === documentSetId)
      .filter(_.jobType =!= DocumentSetCreationJobType.Recluster)
      .filter(_.state =!= DocumentSetCreationJobState.Cancelled)
      .sortBy(_.id)
  }

  override def indexIdsInProcessingOrder = database.seq(processingIdsCompiled)

  override def indexWithDocumentSets(userEmail: String) = database.seq(indexWithDocumentSetsCompiled(userEmail))

  override def index(documentSetId: Long) = database.seq(indexCompiled(documentSetId))
}

object ImportJobBackend extends DbImportJobBackend with org.overviewproject.database.DatabaseProvider
