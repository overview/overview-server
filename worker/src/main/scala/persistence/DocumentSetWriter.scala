/*
 * DocumentSetWriter
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Writes out the query defining a document set to the database.
 * @param userId the owner of the DocumentSet
 */
class DocumentSetWriter(userId: Long) {
  
  def write(query: String)(implicit c: Connection) : Long = {
    val documentSetId =
      SQL("""
          INSERT INTO document_set (query) 
          VALUES ({query})
          """).on("query" -> query).executeInsert().get
     SQL("""
         INSERT INTO document_set_user (document_set_id, user_id)
         VALUES ({documentSetId}, {userId})
         """).on("documentSetId" -> documentSetId, "userId" -> userId).execute()
         
     documentSetId
  }
}