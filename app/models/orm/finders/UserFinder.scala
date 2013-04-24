package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import models.orm.{ Schema, User }

object UserFinder extends Finder {
  /** @return All Users with some access to a DocumentSet. */
  def byDocumentSet(documentSet: Long) : FinderResult[User] = {
    val dsus = DocumentSetUserFinder.byDocumentSet(documentSet)
    join(dsus, Schema.users)((dsu, u) =>
      select(u)
      on(dsu.userEmail === u.email)
    )
  }

  /** @return All Users with the given access to a DocumentSet. */
  def byDocumentSetAndRole(documentSet: Long, role: Ownership.Value) : FinderResult[User] = {
    val dsus = DocumentSetUserFinder.byDocumentSetAndRole(documentSet, role)
    join(dsus, Schema.users)((dsu, u) =>
      select(u)
      on(dsu.userEmail === u.email)
    )
  }

  /** @return All Users with the given email address.
    *
    * The result will have length 0 or 1.
    */
  def byEmail(email: String) : FinderResult[User] = {
    Schema.users.where(_.email === email)
  }

  /** @return All Users with the given ID.
    *
    * The result will have length 0 or 1.
    */
  def byId(id: Long) : FinderResult[User] = {
    Schema.users.where(_.id === id)
  }
}
