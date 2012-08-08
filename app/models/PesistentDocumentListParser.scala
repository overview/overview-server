package models

import DatabaseStructure.{DocumentData, DocumentTagData}

class PersistentDocumentListParser {
  
  def createDocuments(documentData: Seq[DocumentData],
		  			  documentTagData: Seq[DocumentTagData]) : Seq[core.Document] = {
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4))
  } 
 }