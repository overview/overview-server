package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions
import scala.language.postfixOps

import models.orm.Schema
import models.User
import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.{DocumentSetCreationJobType, Ownership}
import com.overviewdocs.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState}
import com.overviewdocs.tree.orm.finders.{ Finder, FinderResult }

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
