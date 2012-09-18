package models

import anorm._
import java.sql.Connection

class PersistentTagSaver {

  def save(documentSetId: Long, name: String)(implicit c: Connection) : Option[Long] = {
    try {
    	SQL("""
    		INSERT INTO tag (name, document_set_id) 
    		VALUES ({name}, {documentSetId})
    		""").on("name" -> name, "documentSetId" -> documentSetId).executeInsert()
    }
    catch { case _ => None }
  }
}
