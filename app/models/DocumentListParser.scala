/*
 * DocumentListParser.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */
package models

import DatabaseStructure._

// FIXME: These methods have mutated so they may no longer be common among subclasses
class DocumentListParser {

  def createDocuments(documentData: Seq[DocumentData],
    documentTagData: Seq[DocumentTagData], documentNodeData: Seq[DocumentNodeData]): Seq[core.Document] = {
    val tagIds = mapDocumentsToTagIds(documentTagData)
    val nodeIds = mapDocumentsToNodeIds(documentNodeData)
    
    documentData.map(d => core.Document(d._1, d._2, d._4, d._3, tagIds.getOrElse(d._1, Nil),
      nodeIds.getOrElse(d._1, Nil)))
  }

  def createDocumentIdList(documentListData: Seq[DocumentListData]): core.DocumentIdList = {
    val documentCount = documentListData.headOption.getOrElse((0l, None))._1
    val documentIds = documentListData.collect { case (_, Some(d)) => d }
    
    core.DocumentIdList(documentIds, documentCount)
  }
  
  protected def groupById[A](data: Seq[(Long, A)]): Map[Long, Seq[A]] = {
    val groupedById = data.groupBy(_._1)

    groupedById.map {
      case (id, dataList) => (id, dataList.map(_._2))
    }
  }

  private def mapDocumentsToTagIds(documentTagData: Seq[DocumentTagData]): Map[Long, Seq[Long]] = {
    groupById(documentTagData)
  }

  private def mapDocumentsToNodeIds(documentNodeData: Seq[DocumentNodeData]): Map[Long, Seq[Long]] ={
    groupById(documentNodeData)
  }
}
