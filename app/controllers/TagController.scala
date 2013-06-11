///
/*
 * TagController.scala
 *
 * OverviewProject
 * Created by Jonas Karlsson, Aug 2012
 */

package controllers

import au.com.bytecode.opencsv.CSVWriter
import java.io.StringWriter
import play.api.data.{Form,FormError}
import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.{SelectionForm,TagForm}
import models.{ OverviewTag, PotentialTag, PersistentTag }
import models.orm.finders.{DocumentFinder,DocumentTagFinder,TagFinder,FinderResult}
import models.orm.stores.{DocumentTagStore}
import models.{IdList,Selection}

object TagController extends Controller {
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

  def indexCsv(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tags = TagFinder.byDocumentSet(documentSetId).withCounts

    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeNext(Array("id", "name", "count", "color"))
    tags.foreach({ case (tag, count) =>
      csvWriter.writeNext(Array(tag.id.toString, tag.name, count.toString, tag.color.getOrElse("")))
    })
    csvWriter.close()
    Ok(stringWriter.toString())
      .as("text/csv")
      .withHeaders(
        CACHE_CONTROL -> "max-age=0",
        CONTENT_DISPOSITION -> "attachment; filename=overview-tags.csv"
      )
  }

  def add(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    SelectionForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      selection => {
        val foundDocuments = DocumentFinder.bySelection(selection)
        val count = DocumentTagStore.insertForTagAndDocuments(tagId, foundDocuments)
        Ok(views.json.Tag.add(count))
      }
    )
  }

  def remove(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    SelectionForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      selection => {
        val documentTags = DocumentTagFinder.byTagAndSelection(tagId, selection)
        val count = DocumentTagStore.delete(documentTags.query)
        Ok(views.json.Tag.remove(count))
      }
    )
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
        val nodeCounts = tag.countsPerNode(IdList.longs(nodeIds).ids)

        Ok(views.json.Tag.nodeCounts(nodeCounts))
      }
    }
  }

}

