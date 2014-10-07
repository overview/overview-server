package controllers

import au.com.bytecode.opencsv.CSVWriter
import play.api.libs.concurrent.Execution.Implicits._
import java.io.StringWriter
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{SelectionBackend,TagDocumentBackend}
import controllers.forms.{TagForm,NodeIdsForm}
import models.orm.finders.{NodeDocumentFinder,TagFinder}
import models.orm.stores.TagStore
import models.OverviewDatabase
import org.overviewproject.tree.orm.Tag

trait TagController extends Controller {
  protected val selectionBackend: SelectionBackend
  protected val tagDocumentBackend: TagDocumentBackend
  protected val storage: TagController.Storage

  def create(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    TagForm(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      unsavedTag => {
        val tag = storage.insertOrUpdate(unsavedTag)
        Ok(views.json.Tag.create(tag))
      }
    )
  }

  def indexJsonWithTree(documentSetId: Long, treeId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId, treeId)
    Ok(views.json.Tag.index(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId)
    Ok(views.json.Tag.index(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def indexCsv(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
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

  def add(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    OverviewDatabase.inTransaction { storage.findTag(documentSetId, tagId) } match {
      case None => Future.successful(NotFound)
      case Some(tag) => {
        val sr = selectionRequest(documentSetId, request)
        selectionBackend.findOrCreate(request.user.email, sr)
          .flatMap(_.getAllDocumentIds)
          .flatMap { (ids) => tagDocumentBackend.createMany(tagId, ids) }
          .map(Unit => Created)
      }
    }
  }

  def remove(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    OverviewDatabase.inTransaction { storage.findTag(documentSetId, tagId) } match {
      case None => Future.successful(NotFound)
      case Some(tag) => {
        val sr = selectionRequest(documentSetId, request)
        selectionBackend.findOrCreate(request.user.email, sr)
          .flatMap(_.getAllDocumentIds)
          .flatMap { (ids) => tagDocumentBackend.destroyMany(tagId, ids) }
          .map(Unit => NoContent)
      }
    }
  }

  def delete(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    OverviewDatabase.inTransaction { storage.findTag(documentSetId, tagId) } match {
      case None => Future.successful(NotFound)
      case Some(tag) => {
        tagDocumentBackend.destroyAll(tagId)
          .map(Unit => OverviewDatabase.inTransaction { storage.delete(tag) })
          .map(Unit => NoContent)
      }
    }
  }

  def update(documentSetId: Long, tagId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
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

  def nodeCounts(documentSetId: Long, tagId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
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
  override protected val tagDocumentBackend = TagDocumentBackend
  override protected val selectionBackend = SelectionBackend

  trait Storage {
    def insertOrUpdate(tag: Tag) : Tag

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

  override protected val storage = new Storage {
    override def insertOrUpdate(tag: Tag) : Tag = {
      TagStore.insertOrUpdate(tag)
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
