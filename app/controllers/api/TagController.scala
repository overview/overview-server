package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{userViewingDocumentSet}
import controllers.backend.TagBackend
import com.overviewdocs.models.Tag

trait TagController extends ApiController {
  protected val tagBackend: TagBackend

  def index(documentSetId: Long) = ApiAuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    tagBackend.index(documentSetId)
      .map(tags => Ok(views.json.api.Tag.index(tags)))
  }
}

object TagController extends TagController {
  override protected val tagBackend = TagBackend
}
