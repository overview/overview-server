package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions
import scala.language.postfixOps

import models.orm.Schema
import models.User
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.{DocumentSetCreationJobType, Ownership}
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState }
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentSetCreationJobFinder extends Finder {
  class DocumentSetCreationJobFinderResult(query: Query[DocumentSetCreationJob]) extends FinderResult(query) {
    def forUpdate: DocumentSetCreationJobFinderResult = toQuery.forUpdate

    def byState(states: DocumentSetCreationJobState.Value*): DocumentSetCreationJobFinderResult = {
      from(toQuery)(dscj =>
        where(dscj.state in states)
        select(dscj)
      )
    }

    def byJobType(jobTypes: DocumentSetCreationJobType.Value*): DocumentSetCreationJobFinderResult = {
      from(toQuery)(dscj =>
        where(dscj.jobType in jobTypes)
        select(dscj)
      )
    }

    def excludeTreeCreationJobs: DocumentSetCreationJobFinderResult = {
      from(toQuery)(dscj =>
        where(dscj.jobType <> DocumentSetCreationJobType.Recluster)
        select(dscj)
      )
    }
    
    def excludeCancelledJobs: DocumentSetCreationJobFinderResult = {
      from(toQuery)(dscj =>
        where (dscj.state <> DocumentSetCreationJobState.Cancelled)
        select(dscj)
      )
    }

    def withDocumentSets: FinderResult[(DocumentSetCreationJob, DocumentSet)] = {
      join(toQuery, Schema.documentSets)((dscj, ds) =>
        select(dscj, ds)
          on (dscj.documentSetId === ds.id))
    }

    def withDocumentSetsAndQueuePositions: FinderResult[(DocumentSetCreationJob, DocumentSet, Long)] = {
      val jobsInQueue = from(Schema.documentSetCreationJobs)(dscj =>
        where(dscj.state === DocumentSetCreationJobState.NotStarted or
          dscj.state === DocumentSetCreationJobState.InProgress or
          dscj.state === DocumentSetCreationJobState.FilesUploaded or
          dscj.state === DocumentSetCreationJobState.TextExtractionInProgress)
          select (dscj))

      // XXX this is O(N^2), unless Postgres has some trick for optimizing it.
      // Use a window function instead?
      val queuePositions = join(toQuery, jobsInQueue.leftOuter)((job, jobsAhead) =>
        groupBy(job.id)
          compute (countDistinct(jobsAhead.map(_.id)))
          on (jobsAhead.map(_.id) lt job.id))

      join(toQuery, Schema.documentSets, queuePositions)((dscj, ds, qp) =>
        where(ds.deleted === false)
          select (dscj, ds, qp.measures)
          orderBy (dscj.id desc)
          on (
            dscj.documentSetId === ds.id,
            qp.key === dscj.id))
    }

    def withDocumentSetsAndOwners: FinderResult[(DocumentSetCreationJob, DocumentSet, User)] = {
      join(toQuery, Schema.documentSets, Schema.documentSetUsers, Schema.users)((dscj, ds, dsu, u) =>
        select(dscj, ds, u)
        orderBy (dscj.id desc)
        on (
          dscj.documentSetId === ds.id,
          ds.id === dsu.documentSetId and dsu.role === Ownership.Owner,
          dsu.userEmail === u.email))
    }
  }

  object DocumentSetCreationJobFinderResult {
    implicit def fromQuery(query: Query[DocumentSetCreationJob]): DocumentSetCreationJobFinderResult = new DocumentSetCreationJobFinderResult(query)
  }

  /** All DocumentSetCreationJobs with the given ID. */
  def byDocumentSet(documentSet: Long): DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.documentSetId === documentSet)
  }

  /** All DocumentSetCreationJobs for the given document sets. */
  def byDocumentSets(documentSets: Iterable[Long]): DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.documentSetId in documentSets)
  }

  /** @return All DocumentSetCreationJobs for the given user. */
  def byUser(user: String): DocumentSetCreationJobFinderResult = {
    join(Schema.documentSetCreationJobs, Schema.documentSetUsers)((dscj, dsu) =>
      select(dscj)
        on (
          dscj.documentSetId === dsu.documentSetId
          and dsu.userEmail === user
          and dsu.role === Ownership.Owner))
  }

  /** @return All DocumentSetCreationJobs that are cloning the given DocumentSet. */
  def bySourceDocumentSet(sourceDocumentSet: Long): DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.sourceDocumentSetId === sourceDocumentSet)
  }

  /** @return All DocumentSetCreationJobs with the given ID (either 0 or 1 rows). */
  def byDocumentSetCreationJob(documentSetCreationJob: Long): DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.id === documentSetCreationJob)
  }

  /**
   * @return All DocumentSetCreationJobs ahead of the given one in the
   * worker's queue.
   */
  def aheadOfJob(job: Long): DocumentSetCreationJobFinderResult = {
    from(Schema.documentSetCreationJobs)(dscj =>
      where(
        dscj.state === DocumentSetCreationJobState.NotStarted
          and dscj.id.~ < job)
        select (dscj))
  }

  /**
   * @return All DocumentSetCreationJobs.
   *
   * These are ordered from newest to oldest.
   */
  def all: DocumentSetCreationJobFinderResult = {
    from(Schema.documentSetCreationJobs)(dscj =>
      select(dscj))

  }
}
