package controllers


import java.sql.Connection
import models.orm.User
import play.api.data.{Form,FormError}
import play.api.db.DB
import play.api.mvc.{Action, AnyContent, Request}
import play.api.Play.current

import models.{PersistentDocumentList,PersistentTag,PersistentTagLoader}

object TagController extends BaseController {

    
  def add(documentSetId: Long, tagName: String) = 
    authorizedAction(userOwningDocumentSet(documentSetId))(user => 
      authorizedAdd(documentSetId, tagName)(_: Request[AnyContent], _: Connection)
    )

  def remove(documentSetId: Long, tagName: String) = 
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedRemove(documentSetId, tagName)(_: Request[AnyContent], _: Connection)
    )

  def delete(documentSetId: Long, tagName: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedDelete(documentSetId, tagName)(_: Request[AnyContent], _: Connection)
    )
      
  def nodeCounts(documentSetId: Long, tagName: String, nodeIds: String) = 
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedNodeCounts(documentSetId, tagName, nodeIds)(_: Request[AnyContent], _: Connection)
    )
    
  private val idListFormat = new play.api.data.format.Formatter[Seq[Long]] {
    def bind(key: String, data: Map[String,String]) : Either[Seq[FormError],Seq[Long]] = {
      val idList = data.get(key).map(s => IdList(s)).getOrElse(Seq())
      Right(idList)
    }

    def unbind(key: String, value: Seq[Long]) = Map(key -> value.mkString(","))
  }
  private val idList = play.api.data.Forms.of(idListFormat)

  def form(documentSetId: Long) = Form(
    play.api.data.Forms.mapping(
      "documents" -> idList,
      "nodes" -> idList,
      "tags" -> idList
    )
    ({ (documents, nodes, tags) =>
      new PersistentDocumentList(documentSetId, nodes, tags, documents)
    })
    ( (documents: PersistentDocumentList) =>
      // FIXME should be: Some((documents.documentIds, documents.nodeIds, documents.tagIds))
      Some((Seq(), Seq(), Seq()))
    )
  )

  def authorizedAdd(documentSetId: Long, tagName: String)
                   (implicit request: Request[AnyContent], connection: Connection) = {
    form(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      documents => {
        val tagData = PersistentTag.findOrCreateByName(tagName, documentSetId)

        val tagUpdateCount = documents.addTag(tagData.id)
        val tag = tagData.loadTag
        val taggedDocuments = tagData.loadDocuments(tag)

        Ok(views.json.Tag.add(tag, tagUpdateCount, taggedDocuments))
      }
    )
  }
  
  
  
  def authorizedRemove(documentSetId: Long, tagName: String)
                      (implicit request: Request[AnyContent], connection: Connection) = {
    PersistentTag.findByName(tagName, documentSetId) match {
      case None => NotFound
      case Some(tagData) => {
        form(documentSetId).bindFromRequest.fold(
          formWithErrors => BadRequest,
          documents => {
            val tagUpdateCount = documents.removeTag(tagData.id)
            val tag = tagData.loadTag
            val taggedDocuments = tagData.loadDocuments(tag)
            
            Ok(views.json.Tag.remove(tag, tagUpdateCount, taggedDocuments))
          }
        )
      }
    }
  }


  def authorizedDelete(documentSetId: Long, tagName: String)
  (implicit request: Request[AnyContent], connection: Connection) = {
    PersistentTag.findByName(tagName, documentSetId) match {
      case None => NotFound
      case Some(tag) => {
	val t = tag.loadTag
	val taggedDocuments = tag.loadDocuments(t)
	
	tag.delete
	Ok
      }
    }
  }

  def authorizedNodeCounts(documentSetId: Long, tagName: String, nodeIds: String) 
                          (implicit request: Request[AnyContent], connection: Connection) = {
    PersistentTag.findByName(tagName, documentSetId) match {
      case None => NotFound
      case Some(tag) => {
        val nodeCounts = tag.countsPerNode(IdList(nodeIds))
        Ok(views.json.Tag.nodeCounts(nodeCounts))
      }
    }
  }
}


