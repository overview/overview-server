package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ DocumentSet, Schema }

object UserFinder {
  /** @return All Users with some access to a DocumentSet. */
  def byDocumentSet(documentSet: Long) = {
    from(Schema.documentSetUsers, Schema.users)((dsu, u) =>
      where(dsu.documentSetId === documentSet and dsu.userEmail === u.email)
      select(u)
    )
  }
}
