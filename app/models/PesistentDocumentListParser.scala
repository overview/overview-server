package models

import DatabaseStructure.DocumentData

class PersistentDocumentListParser {
  
  def createDocuments(documentData: List[DocumentData]) : List[core.Document] = {
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4))
  } 
    

}