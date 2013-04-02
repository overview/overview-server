package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import models.orm.{ DocumentSet, Schema }

object UserFinder {
  /** @return All Users with some access to a DocumentSet. */
  def byDocumentSet(documentSet: Long) = {
    val dsus = DocumentSetUserFinder.byDocumentSet(documentSet)
    join(dsus, Schema.users)((dsu, u) =>
      select(u)
      on(dsu.userEmail === u.email)
    )
  }

  /** @return All Users with the given access to a DocumentSet. */
  def byDocumentSetAndRole(documentSet: Long, role: Ownership.Value) = {
    val dsus = DocumentSetUserFinder.byDocumentSetAndRole(documentSet, role)
    join(dsus, Schema.users)((dsu, u) =>
      select(u)
      on(dsu.userEmail === u.email)
    )
  }
}
