package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ DocumentSet, Schema }

object NodeFinder {
  /** @return All Nodes in a DocumentSet. */
  def byDocumentSet(documentSet: Long) = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet)
      select(n)
    )
  }

  /** @return All Nodes with the given ID in the given documentSet.
   *
   * This returns an org.squeryl.Query. Call `single` on it to return the
   * result or throw an exception; call `.headOption` to make it an Option.
   */
  def byDocumentSetAndId(documentSet: Long, id: Long) = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet and n.id === id)
      select(n)
    )
  }
}
