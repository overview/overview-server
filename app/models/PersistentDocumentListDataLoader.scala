package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader extends DocumentTagDataLoader with PersistentDocumentListSelector {

  def loadSelectedDocumentSlice(documentSetId: Long,
		  						nodeIds: Seq[Long], 
		  					    tagIds: Seq[Long], 
		  					    documentIds: Seq[Long],
		  						firstRow: Long, maxRows: Long)
  		(implicit c: Connection): List[DocumentData] = {
    
    val whereClauses = SelectionWhere(documentSetId, nodeIds, tagIds, documentIds)
    val where = combineWhereClauses(whereClauses)
     
    documentSliceQueryWhere(documentSetId, firstRow, maxRows, where)
  }

  def loadCount(documentSetId: Long,
		  		nodeIds: Seq[Long], 
                tagIds: Seq[Long],
                documentIds: Seq[Long])(implicit c: Connection): Long = {
    val whereClauses = SelectionWhere(documentSetId, nodeIds, tagIds, documentIds)
    val where = combineWhereClauses(whereClauses)
    
    countQueryWhere(documentSetId, where)
  }
  
  private def documentSliceQueryWhere(documentSetId: Long,
                                      firstRow: Long, maxRows: Long, where: String)
                                     (implicit c: Connection) : List[DocumentData] = {
    SQL("""
        SELECT id, title, text_url, view_url FROM document 
        """ + where + 
        """
        ORDER BY id 
        LIMIT {maxRows} OFFSET {offset}
        """).on("maxRows" -> maxRows, "offset" -> firstRow, 
                "documentSetId" -> documentSetId).
        as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map (flatten) *)
  }
  
  private def countQueryWhere(documentSetId: Long, where: String)
                             (implicit c: Connection) : Long = {
    SQL("""
        SELECT COUNT(*) FROM document 
        """ + where  
        ).on("documentSetId" -> documentSetId).as(scalar[Long].single)
  }
}