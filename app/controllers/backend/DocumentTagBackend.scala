package controllers.backend

import scala.concurrent.Future

trait DocumentTagBackend extends Backend {
  /** Gives a list of Tag IDs for each Document.
    *
    * When a Document does not have any Tags, its list will be empty.
    *
    * The returned lists are not ordered.
    */
  def indexMany(documentIds: Seq[Long]): Future[Map[Long,Seq[Long]]]
}

trait DbDocumentTagBackend extends DocumentTagBackend with DbBackend {
  import database.api._
  import database.executionContext

  override def indexMany(documentIds: Seq[Long]) = {
    if (documentIds.isEmpty) {
      Future.successful(Map())
    } else {
      import slick.jdbc.GetResult
      implicit val rconv = GetResult(r => (r.nextLong() -> r.nextArray[Long]()))
      database.run(sql"""
        SELECT document_id, ARRAY_AGG(tag_id)
        FROM document_tag
        WHERE document_id IN (#${documentIds.mkString(",")})
        GROUP BY document_id
      """.as[(Long,Seq[Long])])
        .map { rows =>
          documentIds.map(_ -> Seq[Long]()).toMap ++ rows.toMap
        }
    }
  }
}

object DocumentTagBackend extends DbDocumentTagBackend
