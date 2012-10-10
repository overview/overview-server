package controllers

import controllers.forms.TagForm
import java.sql.Connection
import models.PotentialTag
import models.orm.User
import play.api.data.{ Form, FormError }
import play.api.db.DB
import play.api.mvc.{ Action, AnyContent, Request }
import play.api.Play.current

import models.{ PersistentDocumentList, PersistentTag, PersistentTagLoader }

object TagController extends BaseController {

  def add(documentSetId: Long, tagName: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedAdd(documentSetId, tagName)(_: Request[AnyContent], _: Connection))

  def remove(documentSetId: Long, tagName: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedRemove(documentSetId, tagName)(_: Request[AnyContent], _: Connection))

  def delete(documentSetId: Long, tagName: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedDelete(documentSetId, tagName)(_: Request[AnyContent], _: Connection))

  def update(documentSetId: Long, tagName: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedUpdate(documentSetId, tagName)(_: Request[AnyContent], _: Connection))

  def nodeCounts(documentSetId: Long, tagName: String, nodeIds: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedNodeCounts(documentSetId, tagName, nodeIds)(_: Request[AnyContent], _: Connection))

  private val idListFormat = new play.api.data.format.Formatter[Seq[Long]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Seq[Long]] = {
      val idList = data.get(key).map(s => IdList(s)).getOrElse(Seq())
      Right(idList)
    }

    def unbind(key: String, value: Seq[Long]) = Map(key -> value.mkString(","))
  }
  private val idList = play.api.data.Forms.of(idListFormat)

  def selectionForm(documentSetId: Long) = Form(
    play.api.data.Forms.mapping(
      "documents" -> idList,
      "nodes" -> idList,
      "tags" -> idList)({ (documents, nodes, tags) =>
        new PersistentDocumentList(documentSetId, nodes, tags, documents)
      })((documents: PersistentDocumentList) =>
        // FIXME should be: Some((documents.documentIds, documents.nodeIds, documents.tagIds))
        Some((Seq(), Seq(), Seq()))))

  def authorizedAdd(documentSetId: Long, tagName: String)(implicit request: Request[AnyContent], connection: Connection) = {
    selectionForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      documents => {
	val tag = PotentialTag(tagName).inDocumentSet(documentSetId) match {
	  case Some(t) => t
	  case None => PotentialTag(tagName).create(documentSetId)
	}
	
        val tagUpdateCount = documents.addTag(tag.id)

	val tagInfo = PersistentTag(tag)
        val taggedDocuments = tagInfo.loadDocuments

        Ok(views.json.Tag.add(tagInfo, tagUpdateCount, taggedDocuments))
      })
  }

  def authorizedRemove(documentSetId: Long, tagName: String)(implicit request: Request[AnyContent], connection: Connection) = {
    selectionForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      documents => {
        PotentialTag(tagName).inDocumentSet(documentSetId) match {
	  case None => NotFound
	  case Some(tag) => {
	    val tagUpdateCount = documents.removeTag(tag.id)

	    val tagInfo = PersistentTag(tag)
            val taggedDocuments = tagInfo.loadDocuments

            Ok(views.json.Tag.remove(tagInfo, tagUpdateCount, taggedDocuments))
          }
	}
      }
    )
  }
    

  def authorizedDelete(documentSetId: Long, tagName: String)(implicit request: Request[AnyContent], connection: Connection) = {
    PotentialTag(tagName).inDocumentSet(documentSetId) match {
      case None => NotFound
      case Some(tag) => {
	tag.delete
	Ok(views.json.Tag.delete(tag.id, tag.name))
      }
    }
  }

  def authorizedUpdate(documentSetId: Long, tagName: String)(implicit request: Request[AnyContent], connection: Connection) = {
    TagForm().bindFromRequest.fold(
      formWithErrors => BadRequest,
      formData => {
	PotentialTag(tagName).inDocumentSet(documentSetId) match {
	  case None => NotFound
  	  case Some(tag) => {
	    val updatedTag = tag.withName(formData._1).withColor(formData._2).save
	    val tagInfo = PersistentTag(updatedTag)

    	    Ok(views.json.Tag.update(tagInfo))
	  }
	}
      }
    )
  }

  def authorizedNodeCounts(documentSetId: Long, tagName: String, nodeIds: String)(implicit request: Request[AnyContent], connection: Connection) = {
    PotentialTag(tagName).inDocumentSet(documentSetId) match {
      case None => NotFound
      case Some(tag) => {
	val nodeCounts = PersistentTag(tag).countsPerNode(IdList(nodeIds))
        Ok(views.json.Tag.nodeCounts(nodeCounts))
      }
    }
  }


}

