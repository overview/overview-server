package models.orm.finders

import org.squeryl.Query
import scala.language.postfixOps
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.Ownership
import models.orm.{ DocumentSet, Schema, User }

object DocumentSetCreationJobFinder extends Finder {
  class DocumentSetCreationJobFinderResult(query: Query[DocumentSetCreationJob]) extends FinderResult(query) {
    def byState(states: DocumentSetCreationJobState.Value*) : DocumentSetCreationJobFinderResult = {
      from(toQuery)(dscj =>
        where(dscj.state in states)
        select(dscj)
      )
    }

    def withDocumentSets : FinderResult[(DocumentSetCreationJob, DocumentSet)] = {
      join(toQuery, Schema.documentSets)((dscj, ds) =>
        select(dscj, ds)
        on(dscj.documentSetId === ds.id)
      )
    }

    def withDocumentSetsAndQueuePositions : FinderResult[(DocumentSetCreationJob, DocumentSet, Long)] = {
      val jobsInQueue = from(Schema.documentSetCreationJobs)(dscj =>
        where(dscj.state === DocumentSetCreationJobState.NotStarted or dscj.state === DocumentSetCreationJobState.InProgress)
        select(dscj)
      )

      // XXX this is O(N^2), unless Postgres has some trick for optimizing it.
      // Use a window function instead?
      val queuePositions = join(toQuery, jobsInQueue.leftOuter)((job, jobsAhead) =>
        groupBy(job.id)
        compute(countDistinct(jobsAhead.map(_.id)))
        on(jobsAhead.map(_.id) lt job.id)
      )
        
      join(toQuery, Schema.documentSets, queuePositions)((dscj, ds, qp) =>
        select(dscj, ds, qp.measures)
        on(
          dscj.documentSetId === ds.id,
          qp.key === dscj.id
        )
      )
    }

    def withDocumentSetsAndOwners : FinderResult[(DocumentSetCreationJob, DocumentSet, User)] = {
      join(toQuery, Schema.documentSets, Schema.documentSetUsers, Schema.users)((dscj, ds, dsu, u) =>
        select(dscj, ds, u)
        on(
          dscj.documentSetId === ds.id,
          ds.id === dsu.documentSetId and dsu.role === Ownership.Owner,
          dsu.documentSetId === u.id
        )
      )
    }
  }

  object DocumentSetCreationJobFinderResult {
    implicit def fromQuery(query: Query[DocumentSetCreationJob]) : DocumentSetCreationJobFinderResult = new DocumentSetCreationJobFinderResult(query)
  }

  /** @return All DocumentSetCreationJobs with the given ID.
    *
    * Since ID is a unique key, the return value can only have 0 or 1 row.
    */
  def byDocumentSet(documentSet: Long) : DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.documentSetId === documentSet)
  }

  /** @return All DocumentSetCreationJobs for the given user. */
  def byUser(user: String) : DocumentSetCreationJobFinderResult = {
    join(Schema.documentSetCreationJobs, Schema.documentSetUsers)((dscj, dsu) =>
      select(dscj)
      on(
        dscj.documentSetId === dsu.documentSetId
        and dsu.userEmail === user
        and dsu.role === Ownership.Owner
      )
    )
  }

  /** @return All DocumentSetCreationJobs that are cloning the given DocumentSet. */
  def bySourceDocumentSet(sourceDocumentSet: Long) : DocumentSetCreationJobFinderResult = {
    Schema.documentSetCreationJobs.where(_.sourceDocumentSetId === sourceDocumentSet)
  }

  /** @return All DocumentSetCreationJobs ahead of the given one in the
    * worker's queue.
    */
  def aheadOfJob(job: Long) : DocumentSetCreationJobFinderResult = {
    from(Schema.documentSetCreationJobs)(dscj =>
      where(
        dscj.state === DocumentSetCreationJobState.NotStarted
        and dscj.id.~ < job
      )
      select(dscj)
    )
  }

  /** @return All DocumentSetCreationJobs.
    *
    * These are ordered from newest to oldest.
    */
  def all : DocumentSetCreationJobFinderResult = {
    from(Schema.documentSetCreationJobs)(dscj =>
      select(dscj)
      orderBy(dscj.id desc)
    )
  }
}
