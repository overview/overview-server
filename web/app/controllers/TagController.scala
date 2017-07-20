package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{SelectionBackend,TagBackend}
import controllers.forms.TagForm
import com.overviewdocs.models.Tag

class TagController @Inject() (
  tagBackend: TagBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {
  private val selectionIdKey = "selectionId"

  def create(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    TagForm.forCreate.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        tagBackend.create(documentSetId, attributes)
          .map(tag => Created(views.json.Tag.create(tag)))
      }
    )
  }

  def indexJson(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      tagsWithCounts <- tagBackend.indexWithCounts(documentSetId)
    } yield Ok(views.json.Tag.index.withCounts(tagsWithCounts))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  private def quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
  def indexCsv(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      tagsWithCounts <- tagBackend.indexWithCounts(documentSetId)
    } yield {
      val sb = new StringBuilder("id,name,count,color\r\n")
      tagsWithCounts.foreach { tuple =>
        val (tag: Tag, count: Int) = tuple
        sb.append(s"${tag.id},${quote(tag.name)},$count,${tag.color}\r\n")
      }
      Ok(sb.toString)
        .as("text/csv")
        .withHeaders(
          CACHE_CONTROL -> "max-age=0",
          CONTENT_DISPOSITION -> "attachment; filename=overview-tags.csv"
        )
    }
  }

  def destroy(documentSetId: Long, tagId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    tagBackend.destroy(documentSetId, tagId)
      .map(_ => NoContent)
  }

  def update(documentSetId: Long, tagId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
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
