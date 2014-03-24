package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions

import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.DocumentSetUser
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentSetUserFinder extends Finder {
  class DocumentSetUserFinderResult(query: Query[DocumentSetUser]) extends FinderResult(query) {
    def exceptDeletedDocumentSets : DocumentSetUserFinderResult = {
      join(toQuery, Schema.documentSets)((dsu, ds) =>
        where(ds.deleted === false)
        select(dsu)
        on(dsu.documentSetId === ds.id)
      )
    }
  }

  object DocumentSetUserFinderResult {
    implicit def fromQuery(query: Query[DocumentSetUser]) : DocumentSetUserFinderResult = new DocumentSetUserFinderResult(query)
  }

  /** @return All `DocumentSetUser`s for a DocumentSet.
    *
    * This includes all roles, such as Owner and Viewer.
    */
  def byDocumentSet(documentSet: Long) : DocumentSetUserFinderResult = {
    Schema.documentSetUsers.where(_.documentSetId === documentSet)
  }

  /** @return All `DocumentSetUser`s for a DocumentSet with the given role. */
  def byDocumentSetAndRole(documentSet: Long, role: Ownership.Value) : DocumentSetUserFinderResult = {
    byDocumentSet(documentSet).where(_.role === role)
  }

  /** @return All `DocumentSetUser`s for a User. */
  def byUser(user: String) : DocumentSetUserFinderResult = {
    Schema.documentSetUsers.where(_.userEmail === user)
  }

  /** @return All `DocumentSetUser`s for a User with the given Role. */
  def byUserAndRole(user: String, role: Ownership.Value) : DocumentSetUserFinderResult = {
    from(Schema.documentSetUsers)(dsu =>
      where(dsu.userEmail === user and dsu.role === role)
      select(dsu)
    )
  }

  /** @return All `DocumentSetUser`s for a User and DocumentSet.
    *
    * This can only have 0 or 1 rows.
    */
  def byDocumentSetAndUser(documentSet: Long, user: String) : DocumentSetUserFinderResult = {
    Schema.documentSetUsers.where(dsu =>
      dsu.documentSetId === documentSet
      and dsu.userEmail === user
    )
  }

  /** @return All `DocumentSetUser`s for a given User, DocumentSet and role.
    *
    * This can only have 0 or 1 rows.
    */
  def byDocumentSetAndUserAndRole(documentSet: Long, user: String, role: Ownership.Value) : DocumentSetUserFinderResult = {
    byDocumentSetAndUser(documentSet, user).where(_.role === role)
  }
}
