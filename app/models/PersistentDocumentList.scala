package models

import java.sql.Connection

class PersistentDocumentList(nodeIds: Seq[Long], documentIds: Seq[Long],
							 loader: PersistentDocumentListDataLoader = 
							   new PersistentDocumentListDataLoader(),
							 parser: PersistentDocumentListParser = 
							   new PersistentDocumentListParser()) {
  
  def loadSlice(start: Long, end: Long)(implicit c: Connection) : Seq[core.Document] = {
    require(start >= 0)
    require(start < end)
    
    val documentData = loader.loadSelectedDocumentSlice(nodeIds, documentIds, start, end  - start)
    parser.createDocuments(documentData)
  }
  
  def loadCount()(implicit c: Connection) : Long = {
    loader.loadCount(nodeIds, documentIds)
  }
  
  

}