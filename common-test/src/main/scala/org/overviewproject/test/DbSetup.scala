package org.overviewproject.test

import anorm._
import java.sql.Connection
import org.overviewproject.test.IdGenerator.nextDocumentId

object DbSetup {

  private def failInsert = { throw new Exception("failed insert") }

  def insertDocumentSet(query: String)(implicit c: Connection): Long = {
    SQL("""
      INSERT INTO document_set (public, title, query, created_at, document_count, document_processing_error_count, import_overflow_count, deleted)
      VALUES ('false', {title}, {query}, TIMESTAMP '1970-01-01 00:00:00', 100, 0, 0, 'false')
      """).on(
      'title -> ("From query: " + query),
      'query -> query).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, description: String, documentCloudId: String, title: Option[String] = None)(implicit connection: Connection): Long = {
    SQL("""
        INSERT INTO document (id, document_set_id, description, documentcloud_id, title, created_at)
        VALUES ({id}, {documentSetId}, {description}, {documentCloudId}, {title}, CURRENT_TIMESTAMP)
        """).on(
      "id" -> nextDocumentId(documentSetId),
      "documentSetId" -> documentSetId,
      "description" -> description,
      "documentCloudId" -> documentCloudId,
      "title" -> title).
      executeInsert().getOrElse(failInsert)
  }

  def insertDocuments(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield insertDocument(documentSetId, "description-" + i, "documentCloudId-" + i)
  }
}
