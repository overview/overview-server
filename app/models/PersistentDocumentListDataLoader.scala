package models

import anorm.SQL
import anorm.SqlParser._
import DatabaseStructure._
import java.sql.Connection

class PersistentDocumentListDataLoader extends PersistentDocumentListSelector {

  def loadSelectedDocumentSlice(nodeIds: Seq[Long], documentIds: Seq[Long],
		  						firstRow: Long, maxRows: Long)(implicit c: Connection): List[DocumentData] = {
    
    val whereClauses = SelectionWhere(nodeIds, documentIds)
    val where = combineWhereClauses(whereClauses)
     
    documentSliceQueryWhere(firstRow, maxRows, where)
  }

  def loadCount(nodeIds: Seq[Long], documentIds: Seq[Long])(implicit c: Connection): Long = {
    val whereClauses = SelectionWhere(nodeIds, documentIds)
    val where = combineWhereClauses(whereClauses)
    
    countQueryWhere(where)
  }
  
  private def documentSliceQueryWhere(firstRow: Long, maxRows: Long, where: String)(implicit c: Connection) : List[DocumentData] = {
    SQL("""
        SELECT id, title, text_url, view_url FROM document 
        """ + where + 
        """
        ORDER BY id 
        LIMIT {maxRows} OFFSET {offset}
        """).on("maxRows" -> maxRows, "offset" -> firstRow).
        as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map (flatten) *)
  }
  
  private def countQueryWhere(where: String)(implicit c: Connection) : Long = {
    SQL("""
        SELECT COUNT(*) FROM document 
        """ + where  
        ).as(scalar[Long].single)
  }
}