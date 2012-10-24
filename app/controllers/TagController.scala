///
/*
 * TagController.scala
 *
 * OverviewProject
 * Created by Jonas Karlsson, Aug 2012
 */

package controllers

import controllers.forms.TagForm
import java.sql.Connection
import models.{ OverviewTag, PotentialTag }
import models.orm.User
import play.api.data.{ Form, FormError }
import play.api.db.DB
import play.api.mvc.{ Action, AnyContent, Request }
import play.api.Play.current

import models.{ PersistentDocumentList, PersistentTag, PersistentTagLoader }

object TagController extends BaseController {

  def create(documentSetId: Long) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedCreate(documentSetId)(_: Request[AnyContent], _: Connection))

  def add(documentSetId: Long, tagId: Long) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedAdd(documentSetId, tagId)(_: Request[AnyContent], _: Connection))

  def remove(documentSetId: Long, tagId: Long) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedRemove(documentSetId, tagId)(_: Request[AnyContent], _: Connection))

  def delete(documentSetId: Long, tagId: Long) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedDelete(documentSetId, tagId)(_: Request[AnyContent], _: Connection))

  def update(documentSetId: Long, tagId: Long) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedUpdate(documentSetId, tagId)(_: Request[AnyContent], _: Connection))

  def nodeCounts(documentSetId: Long, tagId: Long, nodeIds: String) =
    authorizedAction(userOwningDocumentSet(documentSetId))(user =>
      authorizedNodeCounts(documentSetId, tagId, nodeIds)(_: Request[AnyContent], _: Connection))

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

  def authorizedCreate(documentSetId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val newTag = PotentialTag("_new_tag").create(documentSetId)
    TagForm(newTag).bindFromRequest.fold(
      formWithErrors => BadRequest,
      updatedTag => {
        updatedTag.save
        val tag = PersistentTag(updatedTag)
        Ok(views.json.Tag.create(tag.id, tag.name))
      })
  }

  def authorizedAdd(documentSetId: Long, tagId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {
        val tag = PersistentTag(t)

        selectionForm(documentSetId).bindFromRequest.fold(
          formWithErrors => BadRequest,
          documents => {
            val tagUpdateCount = documents.addTag(tag.id)
            val taggedDocuments = tag.loadDocuments

            Ok(views.json.Tag.add(tag, tagUpdateCount, taggedDocuments))
          })
      }
    }
  }

  def authorizedRemove(documentSetId: Long, tagId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {
        val tag = PersistentTag(t)

        selectionForm(documentSetId).bindFromRequest.fold(
          formWithErrors => BadRequest,
          documents => {
            val tagUpdateCount = documents.removeTag(tag.id)
            val taggedDocuments = tag.loadDocuments

            Ok(views.json.Tag.remove(tag, tagUpdateCount, taggedDocuments))
          })
      }
    }
  }

  def authorizedDelete(documentSetId: Long, tagId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(tag) => {
        tag.delete
        Ok(views.json.Tag.delete(tag.id, tag.name))
      }
    }
  }

  def authorizedUpdate(documentSetId: Long, tagId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {
        TagForm(t).bindFromRequest.fold(
          formWithErrors => BadRequest,
          updatedTag => {
            updatedTag.save
            val tag = PersistentTag(updatedTag)

            Ok(views.json.Tag.update(tag))
          })
      }
    }
  }

  def authorizedNodeCounts(documentSetId: Long, tagId: Long, nodeIds: String)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {
        val tag = PersistentTag(t)
        val nodeCounts = tag.countsPerNode(IdList(nodeIds))

        Ok(views.json.Tag.nodeCounts(nodeCounts))
      }
    }
  }

}

