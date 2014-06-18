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
import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.{SelectionForm,TagForm,NodeIdsForm}
import org.overviewproject.tree.orm.Tag
import org.overviewproject.tree.orm.finders.FinderResult
import models.orm.finders.{ DocumentFinder, DocumentTagFinder, NodeDocumentFinder, TagFinder }
import models.orm.stores.{DocumentTagStore,TagStore}
import models.Selection

trait TagController extends Controller {
  trait Storage {
    def insertOrUpdate(tag: Tag) : Tag

    /** Adds a tag to a Selection.
      *
      * Security considerations: the caller must ensure the user has access to
      * the given tagId and Selection. (In the case of the Selection, this is
      * accomplished by checking the documentSetId.)
      *
      * Documents in the Selection that are already tagged will be skipped.
      *
      * @return number of documents tagged.
      */
    def addTagToSelection(tagId: Long, selection: Selection) : Int

    /** Removes a tag from a Selection.
      *
      * Security considerations: the caller must ensure the user has access to
      * the given tagId and Selection. (In the case of the Selection, this is
      * accomplished by checking the documentSetId.)
      *
      * Documents in the Selection that are not tagged will be skipped.
      *
      * @return number of documents untagged.
      */
    def removeTagFromSelection(tagId: Long, selection: Selection) : Int

    /** @return a Tag if it exists */
    def findTag(documentSetId: Long, tagId: Long) : Option[Tag]

    /** @return (Tag, docset-count) pairs.  */
    def findTagsWithCounts(documentSetId: Long) : Iterable[(Tag,Long)]

    /** @return (Tag, docset-count, tree-count) tuples. */
    def findTagsWithCounts(documentSetId: Long, treeId: Long) : Iterable[(Tag,Long,Long)]

    /** Returns an Iterable of (nodeId, count) pairs.
      *
      * Security considerations: for speed, we do not verify that the user has
      * access to the given nodeIds. Therefore this method should return every
      * nodeId provided--as 0 if there are no node+tag connections. Furthermore,
      * the node IDs should be returned in the order they are provided, so users
      * can't glean any information about their existence from their ordering.
      *
      * The caller must verify that the user has access to the given tagId. That
      * is enough to ensure the user can't gain information about other users'
      * nodes.
      *
      * @return Iterable of (nodeId, count) pairs
      */
    def tagCountsByNodeId(tagId: Long, nodeIds: Iterable[Long]) : Iterable[(Long,Int)]

    /** Deletes the tag. */
    def delete(tag: Tag) : Unit
  }
  val storage : TagController.Storage

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    TagForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      unsavedTag => {
        val tag = storage.insertOrUpdate(unsavedTag)
        Ok(views.json.Tag.create(tag))
      }
    )
  }

  def indexJsonWithTree(documentSetId: Long, treeId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId, treeId)
    Ok(views.json.Tag.index(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def indexJson(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId)
    Ok(views.json.Tag.index(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def indexCsv(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId)

    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeNext(Array("id", "name", "count", "color"))
    tagsWithCounts.foreach({ case (tag, count) =>
      csvWriter.writeNext(Array(tag.id.toString, tag.name, count.toString, tag.color))
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
        storage.findTag(documentSetId, tagId) match {
          case None => NotFound
          case Some(tag) => {
            val count = storage.addTagToSelection(tagId, selection)
            Ok(views.json.Tag.add(count))
          }
        }
      }
    )
  }

  def remove(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    SelectionForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      selection => {
        storage.findTag(documentSetId, tagId) match {
          case None => NotFound
          case Some(tag) => {
            val count = storage.removeTagFromSelection(tagId, selection)
            Ok(views.json.Tag.remove(count))
          }
        }
      }
    )
  }

  def delete(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    storage.findTag(documentSetId, tagId) match {
      case None => NotFound
      case Some(tag) => {
        storage.removeTagFromSelection(tagId, Selection(documentSetId))
        storage.delete(tag)
        NoContent
      }
    }
  }

  def update(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    storage.findTag(documentSetId, tagId) match {
      case None => NotFound
      case Some(tag) => {
        TagForm(tag).bindFromRequest.fold(
          formWithErrors => BadRequest,
          unsavedTag => {
            val savedTag = storage.insertOrUpdate(unsavedTag)
            Ok(views.json.Tag.update(savedTag))
          }
        )
      }
    }
  }

  def nodeCounts(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    NodeIdsForm().bindFromRequest.fold(
      formWithErrors => BadRequest,
      nodeIds => {
        storage.findTag(documentSetId, tagId) match {
          case None => NotFound
          case Some(tag) => {
            val counts = storage.tagCountsByNodeId(tagId, nodeIds)
            Ok(views.json.helper.nodeCounts(counts))
              .withHeaders(CACHE_CONTROL -> "max-age=0")
          }
        }
      }
    )
  }
}

object TagController extends TagController {
  val storage = new Storage {
    override def insertOrUpdate(tag: Tag) : Tag = {
      TagStore.insertOrUpdate(tag)
    }

    override def addTagToSelection(tagId: Long, selection: Selection) : Int = {
      val foundDocuments = DocumentFinder.bySelection(selection)
      DocumentTagStore.insertForTagAndDocuments(tagId, foundDocuments)
    }

    override def removeTagFromSelection(tagId: Long, selection: Selection) : Int = {
      val documentTags = DocumentTagFinder.byTagAndSelection(tagId, selection)
      DocumentTagStore.delete(documentTags.query)
    }

    override def findTag(documentSetId: Long, tagId: Long) : Option[Tag] = {
      TagFinder.byDocumentSetAndId(documentSetId, tagId).headOption
    }

    override def findTagsWithCounts(documentSetId: Long) : Iterable[(Tag,Long)] = {
      TagFinder
        .byDocumentSet(documentSetId)
        .withCounts
    }

    override def findTagsWithCounts(documentSetId: Long, treeId: Long) : Iterable[(Tag,Long,Long)] = {
      TagFinder
        .byDocumentSet(documentSetId)
        .withCountsForDocumentSetAndTree(treeId)
    }

    override def tagCountsByNodeId(tagId: Long, nodeIds: Iterable[Long]) : Iterable[(Long,Int)] = {
      val counts = NodeDocumentFinder.byNodeIds(nodeIds).tagCountsByNodeId(tagId).toMap
      nodeIds.map(nodeId => (nodeId -> counts.getOrElse(nodeId, 0L).toInt))
    }

    override def delete(tag: Tag) : Unit = {
      // FIXME remove next line
      import org.overviewproject.postgres.SquerylEntrypoint._
      TagStore.delete(tag.id)
    }
  }
}
