package controllers.api

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.Authorities.{userViewingDocumentSet}
import controllers.backend.TagBackend
import com.overviewdocs.models.Tag

class TagController @Inject() (
  tagBackend: TagBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController {

  def index(documentSetId: Long) = apiAuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    tagBackend.index(documentSetId)
      .map(tags => Ok(views.json.api.Tag.index(tags)))
  }
}
