package models

import anorm._
import java.sql.Connection

class PersistentTagSaver {

  def save(name: String, documentSetId: Long)(implicit c: Connection) : Option[Long] = {
    try {
    	SQL("""
    		INSERT INTO tag (id, name, document_set_id) 
    		VALUES (nextval('tag_seq'), {name}, {documentSetId})
    		""").on("name" -> name, "documentSetId" -> documentSetId).executeInsert()
    }
    catch { case _ => None }
  }
}