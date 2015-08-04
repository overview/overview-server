package controllers.api

import play.api.data.{Form,Forms}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.apiDocumentSetCreator
import controllers.backend.{ApiTokenBackend,DocumentSetBackend}
import com.overviewdocs.models.{ApiToken,DocumentSet}

trait DocumentSetController extends ApiController {
  protected val apiTokenBackend: ApiTokenBackend
  protected val backend: DocumentSetBackend

  private val CreateForm = Form[DocumentSet.CreateAttributes](
    Forms.mapping(
      "title" -> Forms.nonEmptyText
    )((title) => DocumentSet.CreateAttributes(title))((a) => Some(a.title))
  )

  def create = ApiAuthorizedAction(apiDocumentSetCreator).async { request =>
    CreateForm.bindFromRequest()(request).fold(
      _ => Future.successful(BadRequest(jsonError("illegal-arguments", "You must pass a JSON object with a 'title' attribute."))),
      attributes => {
        for {
          documentSet <- backend.create(attributes, request.apiToken.createdBy)
          apiToken <- apiTokenBackend.create(Some(documentSet.id), ApiToken.CreateAttributes(request.apiToken.createdBy, "[automatically returned when creating DocumentSet via API]"))
        } yield Created(Json.obj(
          "documentSet" -> Json.obj(
            "id" -> documentSet.id,
            "title" -> documentSet.title
          ),
          "apiToken" -> views.json.ApiToken.show(apiToken)
        ))
      }
    )
  }
}

object DocumentSetController extends DocumentSetController {
  override protected val apiTokenBackend = ApiTokenBackend
  override protected val backend = DocumentSetBackend
}
