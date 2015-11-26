package controllers.backend

import scala.concurrent.Future

import com.overviewdocs.models.CloneJob
import com.overviewdocs.models.tables.CloneJobs

trait CloneJobBackend extends Backend {
  /** Saves and returns a new CloneJob.
    *
    * Be sure to notify the wowrker to begin working on this import; an
    * abandoned CloneJob makes no sense.
    */
  def create(attributes: CloneJob.CreateAttributes): Future[CloneJob]

  /** Marks a CloneJob as "cancelled", if it exists.
    *
    * A CloneJob may complete as usual, or the user may cancel it. The worker
    * will skip some steps if it's cancelled.
    */
  def cancel(documentSetId: Long, id: Int): Future[Unit]
}

trait DbCloneJobBackend extends CloneJobBackend with DbBackend {
  import database.api._
  import database.executionContext

  private lazy val inserter = CloneJobs.map(_.createAttributes).returning(CloneJobs)

  private lazy val cancelCompiled = Compiled { (documentSetId: Rep[Long], cloneJobId: Rep[Int]) =>
    CloneJobs
      .filter(_.id === cloneJobId)
      .filter(_.destinationDocumentSetId === documentSetId)
      .map(_.cancelled)
  }

  override def create(attributes: CloneJob.CreateAttributes): Future[CloneJob] = {
    database.run(inserter.+=(attributes))
  }

  override def cancel(documentSetId: Long, id: Int): Future[Unit] = {
    database.runUnit(cancelCompiled(documentSetId, id).update(true))
  }
}

object CloneJobBackend extends DbCloneJobBackend
