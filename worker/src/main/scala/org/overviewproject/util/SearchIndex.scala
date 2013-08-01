package org.overviewproject.util

import scala.collection.JavaConverters._
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.cluster.metadata.AliasAction
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.index.query.FilterBuilders
import org.elasticsearch.common.xcontent.XContentBuilder

object SearchIndex {

  private val IndexName = "documents_v1"
  private val IndexAlias = "documents"
  private def DocumentSetAlias(id: Long) = s"documents_$id"
  private val DocumentTypeName = "document"
  private val LongType = "integer"
  private val StringType = "string"
    
  private val DocumentSetIdField = "document_set_id"
  private val IdField = "id"
  private val TextField = "text"
  private val SuppliedIdField = "supplied_id"
  private val TitleField = "title"
    
  private val node = nodeBuilder.node
  private val client = node.client
  private val admin = client.admin.indices
  
  def createIndexIfNotExisting = {
    
    if (!indexExists) {
      createIndex
      createMapping
    }
    if (!aliasExists) createAlias
  }
  
  
  def createDocumentSetAlias(documentSetId: Long): Unit = {
    val filter = FilterBuilders.termFilter(DocumentSetIdField, documentSetId)
    val alias = new AliasAction(AliasAction.Type.ADD, IndexName, DocumentSetAlias(documentSetId), filter.toString).routing(s"$documentSetId")
    admin.prepareAliases().addAliasAction(alias).execute.actionGet
  }
  
  def indexDocument(documentSetId: Long, id: Long, text: String, title: Option[String], suppliedId: Option[String]): Unit = {
    val indexRequest = client.prepareIndex(DocumentSetAlias(documentSetId), "document")
    
    val source = addFields(jsonBuilder.startObject, Seq(
      (DocumentSetIdField, documentSetId),
      (IdField, id),
      (TextField, text),
      (TitleField, title),
      (SuppliedIdField, suppliedId)
    )).endObject 
      
    val r = indexRequest.setSource(source).execute.actionGet()
  }
  
  def shutdown: Unit = node.close


  private def indexExists: Boolean = {
    val r = admin.prepareExists(IndexName).execute.actionGet()
    r.isExists
  }
  
  private def createIndex: Unit = admin.prepareCreate(IndexName).execute.actionGet
  
  private def aliasExists: Boolean = {
    val r = admin.prepareExistsAliases(IndexAlias).execute.actionGet
    r.isExists
  } 
  
  private def createAlias: Unit = admin.prepareAliases.addAlias(IndexName, IndexAlias).execute.actionGet 

  
  private def addFields(builder: XContentBuilder, fields: Seq[(String, Any)]): XContentBuilder = {
    fields match {
      case Nil => builder
      case (f, Some(v)) :: tail => addFields(builder.field(f, v), tail)
      case (f, None) :: tail => addFields(builder, tail)
      case (f, v) :: tail => addFields(builder.field(f, v), tail)
    }
  } 
    
  private def createMapping: Unit = {
      
    val mapping =jsonBuilder.startObject
      .startObject(DocumentTypeName)
        .startObject("properties")
          .startObject(DocumentSetIdField)
            .field("type", LongType)
          .endObject
          .startObject(IdField)
            .field("type", StringType)
          .endObject
          .startObject(TextField)
            .field("type", StringType)
          .endObject
          .startObject(SuppliedIdField)
            .field("type", StringType)
          .endObject
          .startObject(TitleField)
            .field("type", StringType)
          .endObject
        .endObject
      .endObject
    
    admin.preparePutMapping(IndexName)
      .setType(DocumentTypeName)
      .setSource(mapping)
      .execute.actionGet
  }
}