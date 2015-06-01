package controllers.backend

import scala.concurrent.Future

trait DocumentTagBackend extends Backend {
  /** Gives a list of Node IDs for each Document.
    *
    * There are no empty lists: they are not defined.
    *
    * The returned lists are not ordered.
    */
  def indexMany(documentIds: Seq[Long]): Future[Map[Long,Seq[Long]]]
}

trait DbDocumentTagBackend extends DocumentTagBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._
  import scala.concurrent.ExecutionContext.Implicits._

  override def indexMany(documentIds: Seq[Long]) = {
    if (documentIds.isEmpty) {
      Future.successful(Map())
    } else {
      import org.overviewproject.database.Slick.Implicit.PgArrayPositionedResult
      import slick.jdbc.{GetResult, StaticQuery => Q}
      implicit val rconv = GetResult(r => (r.nextLong() -> r.nextArray[Long]()))
      val query = Q.queryNA[(Long,Seq[Long])](s"""
        SELECT document_id, ARRAY_AGG(tag_id)
        FROM document_tag
        WHERE document_id IN (${documentIds.mkString(",")})
        GROUP BY document_id
      """)

      db { session => query.list(session).toMap }
    }
  }
}

object DocumentTagBackend extends DbDocumentTagBackend with DbBackend
