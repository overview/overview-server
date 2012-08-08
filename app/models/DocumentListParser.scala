package models

import DatabaseStructure.{DocumentData, DocumentTagData}

class DocumentListParser {
  
  def createDocuments(documentData: Seq[DocumentData],
		  			  documentTagData: Seq[DocumentTagData]) : Seq[core.Document] = {
    val tagIds = mapDocumentsToTagIds(documentTagData)
    
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4, tagIds.getOrElse(d._1, Nil)))
  } 
  
  protected def groupById[A](data: Seq[(Long, A)]) : Map[Long, Seq[A]] = {
    val groupedById = data.groupBy(_._1)
    
    groupedById.map {
      case (id, dataList) => (id, dataList.map(_._2))
    }
  }

  private def mapDocumentsToTagIds(documentTagData: Seq[DocumentTagData]) :
	  Map[Long, Seq[Long]] = {
    groupById(documentTagData)
  }

 }