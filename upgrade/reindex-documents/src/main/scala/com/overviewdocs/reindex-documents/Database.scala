package com.overviewdocs.upgrade.reindex_documents

import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.duration.Duration
import slick.driver.PostgresDriver.api._

class Database(url: PostgresUrl) {
  lazy val db = Database.forURL(url.toJdbcUrl)
  private def await[T](f: Future[T]): T = blocking(Await.result(f, Duration.Inf))

  def close: Unit = db.shutdown

  def getDocumentSetIds: Seq[(Long,Int)] = {
    await(db.run(sql"SELECT id, document_count FROM document_set ORDER BY id".as[(Long,Int)]))
  }

  private def getDocumentIds(documentSetId: Long): Seq[Long] = {
    await(db.run(sql"SELECT UNNEST(sorted_document_ids) FROM document_set WHERE id = $documentSetId".as[Long]))
  }

  private def getDocuments(ids: Seq[Long]): Seq[Document] = {
    await(db.run(sql"""
      SELECT id, document_set_id, COALESCE(text, ''), COALESCE(title, ''), COALESCE(supplied_id, '')
      FROM document
      WHERE id IN (#${ids.mkString(",")})
    """.as[(Long,Long,String,String,String)])).map((Document.apply _).tupled)
  }

  def forEachBatchOfDocumentsInSet(documentSetId: Long, nPerBatch: Int)(f: Seq[Document] => Unit): Unit = {
    getDocumentIds(documentSetId).grouped(nPerBatch).foreach { ids =>
      if (ids.nonEmpty) {
        val documents = getDocuments(ids)
        f(documents)
      }
    }
  }
}
