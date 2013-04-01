package models.orm.finders

import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import models.orm.{ DocumentSet, Schema }

object DocumentSetFinder {
  /** @return All completed `DocumentSet`s with the given ID. */
  def byDocumentSet(documentSet: Long) = {
    from(Schema.documentSets)(ds =>
      where(ds.id === documentSet)
      select(ds)
    )
  }

  /** @return All `DocumentSet`s with the given isPublic valid. */
  def byIsPublic(isPublic: Boolean) = {
    from(Schema.documentSets)(ds =>
      where(ds.isPublic === isPublic)
      select(ds)
      orderBy(ds.createdAt desc)
    )
  }

  /** @return All completed `DocumentSet`s for whith the given user has the given role.
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  private def byUserWithRole(user: String, role: Ownership.Value) = {
    from(Schema.documentSets)(ds =>
      where(
        ds.id in documentSetIdsForUser(user, role)
        and (ds.id notIn documentSetIdsWithCreationJobs)
      )
      select(ds)
      orderBy(ds.createdAt desc)
    )
  }

  /** @return All completed `DocumentSet`s owned by the given user.
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  def byOwner(user: String) = {
    byUserWithRole(user, Ownership.Owner)
  }

  /** @return All completed `DocumentSet`s for which the specified user is a viewer
    *
    * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
    */
  def byViewer(user: String) = {
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
