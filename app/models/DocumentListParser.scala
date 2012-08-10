package models

import DatabaseStructure.{DocumentData, DocumentTagData, TagData}

class DocumentListParser {
  
  def createDocuments(documentData: Seq[DocumentData],
		  			  documentTagData: Seq[DocumentTagData]) : Seq[core.Document] = {
    val tagIds = mapDocumentsToTagIds(documentTagData)
    
    documentData.map(d => core.Document(d._1, d._2, d._3, d._4, tagIds.getOrElse(d._1, Nil)))
  } 
  
  
  def createTags(tagData: Seq[TagData]) : Seq[core.Tag] = {
    val tagNames = mapTagsToNames(tagData)
    val tagDocumentCounts = mapTagsToDocumentCounts(tagData)
    val tagDocuments = mapTagsToDocuments(tagData)
    
    val tagIds = tagNames.keys.toSeq
    tagIds.map(id => core.Tag(id, tagNames(id), 
    				          core.DocumentIdList(tagDocuments(id), tagDocumentCounts(id))))

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
  
      
  private def mapTagsToNames(tagData: Seq[TagData]) : Map[Long, String] = {
    tagData.map(d => (d._1, d._2)).distinct.toMap
  }
  
  private def mapTagsToDocumentCounts(tagData: Seq[TagData]) : Map[Long, Long] = {
    tagData.map(d => (d._1, d._3)).distinct.toMap
  }
  
  private def mapTagsToDocuments(tagData: Seq[TagData]) : Map[Long, Seq[Long]] = {
    val tagAndDocument = tagData.map(d => (d._1, d._4))
    val tagsToPossibleDocuments = groupById(tagAndDocument)
    
    tagsToPossibleDocuments.map {
      case (tag, documents) => (tag -> documents.collect { case Some(id) => id}) 
    }
  }


 }