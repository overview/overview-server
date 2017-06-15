package controllers.api

import javax.inject.Inject
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{userViewingDocumentSet}
import controllers.backend.TagBackend
import com.overviewdocs.models.Tag

class TagController @Inject() (
  tagBackend: TagBackend
) extends ApiController {

  def index(documentSetId: Long) = ApiAuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    tagBackend.index(documentSetId)
      .map(tags => Ok(views.json.api.Tag.index(tags)))
  }
}
