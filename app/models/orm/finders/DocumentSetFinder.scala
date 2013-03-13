package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{DocumentSet,Schema}

object DocumentSetFinder {
  /** @return All completed `DocumentSet`s with the given ID. */
  def byDocumentSet(documentSet: Long) = {
    from(Schema.documentSets)(ds =>
      where(ds.id === documentSet)
      select(ds)
    )
  }

  /** @return All completed `DocumentSet`s owned by the given user.
   *
   * Any DocumentSet that has a DocumentSetCreationJob will _not_ be returned.
   */
  def byUser(user: String) = {
	import scala.language.postfixOps
	
    from(Schema.documentSets)(ds =>
      where(
        ds.id in documentSetIdsForUser(user)
        and (ds.id notIn documentSetIdsWithCreationJobs)
      )
      select(ds)
      orderBy(ds.createdAt desc)
    )
  }

  /** @return List of document set IDs, for use in an IN clause.
   *
   * Every ID returned will be owned by the given user.
   *
   * Example usage:
   *
   *   val ids = documentSetIdsForUser(user)
   *   val documentSets = from(Schema.documentSets)(ds =>
   *     where(ds.id in ids)
   *     select(ds)
   *   )
   */
  protected[finders] def documentSetIdsForUser(user: String) = {
    from(Schema.documentSetUsers)(dsu =>
      where(dsu.userEmail === user)
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
