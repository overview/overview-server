package controllers.backend

import scala.concurrent.Future

import models.Selection

trait DocumentNodeBackend extends Backend {
  /** Gives a list of Node IDs for each Document.
    *
    * There are no empty lists: they are not defined.
    *
    * The returned lists are not ordered.
    */
  def indexMany(documentIds: Seq[Long]): Future[Map[Long,Seq[Long]]]

  /** Counts how many NodeDocuments exist, counted by Node.
    *
    * There are no zero counts: they are not defined.
    *
    * Security implications: a user can query across multiple trees. The user
    * is restricted to the current document set by <tt>selection</tt>.
    *
    * @param selection The documents we care about
    * @param nodeIds The nodes we care about
    */
  def countByNode(selection: Selection, nodeIds: Seq[Long]): Future[Map[Long,Int]]
}

trait DbDocumentNodeBackend extends DocumentNodeBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._
  import scala.concurrent.ExecutionContext.Implicits._

  private def countByDocumentsAndNodes(documentIds: Seq[Long], nodeIds: Seq[Long]): Future[Map[Long,Int]] = {
    if (documentIds.isEmpty) {
      Future.successful(Map())
    } else if (nodeIds.isEmpty) {
      Future.successful(Map())
    } else {
      // Slick is slow at compiling queries. We need this query _fast_
      import scala.slick.jdbc.{GetResult, StaticQuery => Q}
      val query = Q.queryNA[(Long,Int)](s"""
        SELECT node_id, COUNT(*)
        FROM node_document
        WHERE node_id IN (${nodeIds.mkString(",")})
          AND document_id IN (${documentIds.mkString(",")})
        GROUP BY node_id
      """)

      db { session => query.list(session).toMap }
    }
  }

  override def indexMany(documentIds: Seq[Long]) = {
    if (documentIds.isEmpty) {
      Future.successful(Map())
    } else {
      import org.overviewproject.database.Slick.Implicit.PgArrayPositionedResult
      import scala.slick.jdbc.{GetResult, StaticQuery => Q}
      implicit val rconv = GetResult(r => (r.nextLong() -> r.nextLongArray()))
      val query = Q.queryNA[(Long,Seq[Long])](s"""
        SELECT document_id, ARRAY_AGG(node_id)
        FROM node_document
        WHERE document_id IN (${documentIds.mkString(",")})
        GROUP BY document_id
      """)

      db { session => query.list(session).toMap }
    }
  }

  override def countByNode(selection: Selection, nodeIds: Seq[Long]) = {
    for {
      documentIds <- selection.getAllDocumentIds
      result <- countByDocumentsAndNodes(documentIds, nodeIds)
    } yield result
  }
}

object DocumentNodeBackend extends DbDocumentNodeBackend with DbBackend
