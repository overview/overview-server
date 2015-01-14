package controllers

import au.com.bytecode.opencsv.CSVWriter
import play.api.libs.concurrent.Execution.Implicits._
import java.io.StringWriter
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{SelectionBackend,TagBackend,TagDocumentBackend}
import controllers.forms.TagForm
import models.orm.finders.TagFinder
import org.overviewproject.tree.orm.{Tag=>DeprecatedTag}
import org.overviewproject.models.Tag

trait TagController extends Controller {
  protected val selectionBackend: SelectionBackend
  protected val storage: TagController.Storage
  protected val tagBackend: TagBackend
  protected val tagDocumentBackend: TagDocumentBackend

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    TagForm.forCreate.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        tagBackend.create(documentSetId, attributes)
          .map(tag => Created(views.json.Tag.create(tag)))
      }
    )
  }

  def indexJsonWithTree(documentSetId: Long, treeId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId, treeId)
    Ok(views.json.Tag.index.withDocsetCountsAndTreeCounts(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val tagsWithCounts = storage.findTagsWithCounts(documentSetId)
    Ok(views.json.Tag.index.withDocsetCounts(tagsWithCounts))
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
    tagBackend.show(documentSetId, tagId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(tag) => {
        val sr = selectionRequest(documentSetId, request)
        selectionBackend.findOrCreate(request.user.email, sr)
          .flatMap(_.getAllDocumentIds)
          .flatMap { (ids) => tagDocumentBackend.createMany(tagId, ids) }
          .map(Unit => Created)
      }
    })
  }

  def remove(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    tagBackend.show(documentSetId, tagId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(tag) => {
        val sr = selectionRequest(documentSetId, request)
        selectionBackend.findOrCreate(request.user.email, sr)
          .flatMap(_.getAllDocumentIds)
          .flatMap { (ids) => tagDocumentBackend.destroyMany(tagId, ids) }
          .map(Unit => NoContent)
      }
    })
  }

  def destroy(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    tagBackend.destroy(documentSetId, tagId)
      .map(_ => NoContent)
  }

  def update(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    TagForm.forUpdate.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        tagBackend.update(documentSetId, tagId, attributes).map(_ match {
          case None => NotFound
          case Some(tag) => Ok(views.json.Tag.update(tag))
        })
      }
    )
  }
}

object TagController extends TagController {
  override protected val selectionBackend = SelectionBackend
  override protected val tagBackend = TagBackend
  override protected val tagDocumentBackend = TagDocumentBackend

  trait Storage {
    /** @return (Tag, docset-count) pairs.  */
    def findTagsWithCounts(documentSetId: Long) : Seq[(Tag,Long)]

    /** @return (Tag, docset-count, tree-count) tuples. */
    def findTagsWithCounts(documentSetId: Long, treeId: Long) : Seq[(Tag,Long,Long)]
  }

  override protected val storage = new Storage {
    override def findTagsWithCounts(documentSetId: Long) : Seq[(Tag,Long)] = {
      TagFinder
        .byDocumentSet(documentSetId)
        .withCounts
        .toSeq
        .map((t: Tuple2[DeprecatedTag,Long]) => (t._1.toTag, t._2))
    }

    override def findTagsWithCounts(documentSetId: Long, treeId: Long) : Seq[(Tag,Long,Long)] = {
      TagFinder
        .byDocumentSet(documentSetId)
        .withCountsForDocumentSetAndTree(treeId)
        .toSeq
        .map((t: Tuple3[DeprecatedTag,Long,Long]) => (t._1.toTag, t._2, t._3))
    }
  }
}
