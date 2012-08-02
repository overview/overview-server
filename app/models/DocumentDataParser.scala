package models

import models.DatabaseStructure.DocumentData

class DocumentDataParser {

  def createDocument(documentData: Option[DocumentData]) : Option[core.Document] = {
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4))
  }
}