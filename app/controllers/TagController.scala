///
/*
 * TagController.scala
 *
 * OverviewProject
 * Created by Jonas Karlsson, Aug 2012
 */

package controllers

import models.{ OverviewTag, PotentialTag }
import models.orm.User
import play.api.data.{ Form, FormError }
import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.TagForm
import controllers.util.IdList
import models.{ PersistentDocumentList, PersistentTag, PersistentTagLoader }

object TagController extends Controller {
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

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    val newTag = PotentialTag("_new_tag").create(documentSetId)
    TagForm(newTag).bindFromRequest.fold(
      formWithErrors => BadRequest,
      updatedTag => {
        updatedTag.save
        val tag = PersistentTag(updatedTag)
        Ok(views.json.Tag.create(tag.id, tag.name))
      })
  }

  def add(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {

        selectionForm(documentSetId).bindFromRequest.fold(
          formWithErrors => BadRequest,
          documents => {
            val tagUpdateCount = documents.addTag(t.id)
            // Creation of PersistentTag has to happen after tags are added, or documents are not loaded properly.
            val tag = PersistentTag(t)
            val taggedDocuments = tag.loadDocuments

            Ok(views.json.Tag.add(tag, tagUpdateCount, taggedDocuments))
          })
      }
    }
  }

  def remove(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(t) => {
        selectionForm(documentSetId).bindFromRequest.fold(
          formWithErrors => BadRequest,
          documents => {
            val tagUpdateCount = documents.removeTag(t.id)

            val tag = PersistentTag(t)
            val taggedDocuments = tag.loadDocuments

            Ok(views.json.Tag.remove(tag, tagUpdateCount, taggedDocuments))
          })
      }
    }
  }

  def delete(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    OverviewTag.findById(documentSetId, tagId) match {
      case None => NotFound
      case Some(tag) => {
        tag.delete
        Ok(views.json.Tag.delete(tag.id, tag.name))
      }
    }
  }

  def update(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
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

  def nodeCounts(documentSetId: Long, tagId: Long, nodeIds: String) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
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

