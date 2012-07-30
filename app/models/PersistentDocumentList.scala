package models

import java.sql.Connection

class PersistentDocumentList(nodeIds: String, documentIds: String,
							 loader: PersistentDocumentListDataLoader = 
							   new PersistentDocumentListDataLoader(),
							 parser: PersistentDocumentListParser = 
							   new PersistentDocumentListParser()) {
  
  private val nodes = IdList(nodeIds)
  private val documents = IdList(documentIds)
  
  def loadSlice(start: Long, end: Long)(implicit c: Connection) : List[core.Document] = {
    val documentData = loader.loadSelectedDocumentSlice(nodes, documents, start, end  - start)
    parser.createDocuments(documentData)
  }
  
  

}