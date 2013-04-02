package models.orm.finders

import org.squeryl.Query

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import models.orm.{ DocumentSetUser, Schema }

object DocumentSetUserFinder extends Finder {
  /** @return All `DocumentSetUser`s for a DocumentSet.
    *
    * This includes all roles, such as Owner and Viewer.
    */
  def byDocumentSet(documentSet: Long) : FinderResult[DocumentSetUser] = {
    Schema.documentSetUsers.where(_.documentSetId === documentSet)
  }

  /** @return All `DocumentSetUser`s for a DocumentSet with the given role. */
  def byDocumentSetAndRole(documentSet: Long, role: Ownership.Value) : FinderResult[DocumentSetUser] = {
    byDocumentSet(documentSet).where(_.role === role)
  }

  /** @return All `DocumentSetUser`s for a User. */
  def byUser(user: String) : FinderResult[DocumentSetUser] = {
    Schema.documentSetUsers.where(_.userEmail === user)
  }

  /** @return All `DocumentSetUser`s for a User with the given Role. */
  def byUserAndRole(user: String, role: Ownership.Value) : FinderResult[DocumentSetUser] = {
    byUser(user).where(_.role === role)
  }

  /** @return All `DocumentSetUser`s for a User and DocumentSet.
    *
    * This can only have 0 or 1 rows.
    */
  def byDocumentSetAndUser(documentSet: Long, user: String) : FinderResult[DocumentSetUser] = {
    Schema.documentSetUsers.where(dsu =>
      dsu.documentSetId === documentSet
      and dsu.userEmail === user
    )
  }

  /** @return All `DocumentSetUser`s for a given User, DocumentSet and role.
    *
    * This can only have 0 or 1 rows.
    */
  def byDocumentSetAndUserAndRole(documentSet: Long, user: String, role: Ownership.Value) : FinderResult[DocumentSetUser] = {
    byDocumentSetAndUser(documentSet, user).where(_.role === role)
  }
}
