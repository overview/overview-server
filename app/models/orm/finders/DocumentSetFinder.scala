package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{DocumentSet,Schema}
import models.orm.DocumentSetUserRoleType._

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
  def byUser(user: String): Seq[DocumentSet] = {
	import scala.language.postfixOps
	

    val dsWithDsu = from(Schema.documentSets, Schema.documentSetUsers)((ds, dsu) =>
      where(
        ds.id in documentSetIdsForUser(user)
        and (ds.id notIn documentSetIdsWithCreationJobs)
        and ds.id === dsu.documentSetId
      )
      select((ds, dsu))
      orderBy(ds.createdAt desc)
    ).filter(r => r._2.userEmail == user && r._2.role == Owner) 	// FIXME: Have to be able to use enum in query
        
    dsWithDsu.map(_._1).toSeq
  }

  /**
   * @return DocumentSets for which the specified user is a viewer
   */
  def byViewer(user: String) = {
    val viewableDocumentSetsWithRole = from(Schema.documentSets, Schema.documentSetUsers)((ds, dsu) => 
      where(
        dsu.userEmail === user and
        dsu.documentSetId === ds.id
      )
      select((ds, dsu.role))).filter(_._2 == Viewer)
      
    viewableDocumentSetsWithRole.map(_._1)
        
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
