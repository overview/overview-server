package persistence

import anorm._
import java.sql.Connection

trait PersistentDocumentCloudDocument {
  val title: String
  val documentCloudId: String
  
  def write(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (title, documentcloud_id, document_set_id) VALUES
          ({title}, {documentCloudId}, {documentSetId})
        """).on("title" -> title, "documentCloudId" -> documentCloudId,
                "documentSetId" -> documentSetId).executeInsert().get
  }
}