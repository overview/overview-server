package models.orm.finders


import scala.language.postfixOps
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import org.squeryl.Query

import models.orm.{ Schema, User }


object DocumentSetFinder extends Finder {
  class DocumentSetResult(query: Query[DocumentSet]) extends FinderResult(query) {
    def withOwners : FinderResult[(DocumentSet,User)] = {
      join(toQuery, Schema.documentSetUsers, Schema.users)((ds, dsu, u) =>
        where(dsu.role === Ownership.Owner)
        select(ds, u)
        on(
          ds.id === dsu.documentSetId,
          dsu.userEmail === u.email
        )
      )
    }
    
    def withTreeIds: FinderResult[(DocumentSet, Long)] = {
      join(toQuery, Schema.trees)((ds, t) => 
        select(ds, t.id)
        orderBy(ds.createdAt desc)
        on(t.documentSetId === ds.id)
      )
    }
  }
  
  object DocumentSetResult {
    implicit def fromQuery(query: Query[DocumentSet]) = new DocumentSetResult(query)
  }

  /** @return All completed `DocumentSet`s with the given ID.
    *
    * This can have 0 or 1 row.
    */
  def byDocumentSet(documentSet: Long) : DocumentSetResult = {
    Schema.documentSets.where(_.id === documentSet)
  }

  /** @return All completed `DocumentSet`s with the given ID and isPublic.
    *
    * This can have 0 or 1 row.
    */
  def byDocumentSetAndIsPublic(documentSet: Long, isPublic: Boolean) : DocumentSetResult = {
    byDocumentSet(documentSet).where(_.isPublic === isPublic)
  }

  /** @return All `DocumentSet`s with the given isPublic valid. */
  def byIsPublic(isPublic: Boolean) : DocumentSetResult = {
    from(Schema.documentSets)(ds =>
      where(ds.isPublic === isPublic)
      select(ds)
      orderBy(ds.createdAt desc)
    )
  }

  /** @return All `DocumentSet`s with the given title. */
  def byTitle(title: String) : DocumentSetResult = {
    Schema.documentSets.where(_.title === title)
  }

  /** @return All completed `DocumentSet`s for with the given user has the given role.
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  private def byUserWithRole(user: String, role: Ownership.Value) : DocumentSetResult = {
    from(Schema.documentSets)(ds =>
      where(
        ds.id in documentSetIdsForUser(user, role)
        and (ds.id notIn documentSetIdsWithCreationJobs)
        and (ds.deleted === false)
      )
      select(ds)
      orderBy(ds.createdAt desc)
    )
  }

  /** @return All completed `DocumentSet`s owned by the given user.
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  def byOwner(user: String) : DocumentSetResult = {
    byUserWithRole(user, Ownership.Owner)
  }

  /** @return All completed `DocumentSet`s for which the specified user is a viewer
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  def byViewer(user: String) : DocumentSetResult = {
    byUserWithRole(user, Ownership.Viewer)
  }

  /** @return List of document set IDs, for use in an IN clause.
    *
    * Every ID returned will have the exact ownership specified.
    *
    * Example usage:
    *
    *   val ids = documentSetIdsForUser(user, Ownership.Owner)
    *   val documentSets = from(Schema.documentSets)(ds =>
    *     where(ds.id in ids)
    *     select(ds)
    *   )
    */
  protected[finders] def documentSetIdsForUser(user: String, ownership: Ownership.Value) = {
    from(Schema.documentSetUsers)(dsu =>
      where(
        dsu.userEmail === user
        and dsu.role === ownership
      )
      select(dsu.documentSetId)
    )
  }

  /** @return List of document set IDs, for use in an IN clause.
    *
    * Every ID returned will have a DocumentSetCreationJob.
    */
  protected[finders] def documentSetIdsWithCreationJobs = {
    from(Schema.documentSetCreationJobs)(dscj =>
      select(dscj.documentSetId)
    )
  }
}
