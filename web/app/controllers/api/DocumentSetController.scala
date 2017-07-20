package controllers.api

import javax.inject.Inject
import play.api.data.{Form,Forms}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsObject,Json}
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}

import controllers.auth.Authorities.apiDocumentSetCreator
import controllers.backend.{ApiTokenBackend,DocumentSetBackend}
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{ApiToken,DocumentSet}

class DocumentSetController @Inject() (
  apiTokenBackend: ApiTokenBackend,
  backend: DocumentSetBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController {

  private val CreateForm = Form[DocumentSet.CreateAttributes](
    Forms.mapping(
      "title" -> Forms.nonEmptyText
    )((title) => DocumentSet.CreateAttributes(title))((a) => Some(a.title))
  )

  def create = apiAuthorizedAction(apiDocumentSetCreator).async { request =>
    CreateForm.bindFromRequest()(request).fold(
      _ => Future.successful(BadRequest(jsonError("illegal-arguments", "You must pass a JSON object with a 'title' attribute."))),
      attributes => {
        val metadataSchemaOrError: Either[String, MetadataSchema] = request.body.asJson match {
          case None => Right(MetadataSchema.empty)
          case Some(jsValue) => jsValue.asOpt[JsObject] match {
            case None => Left("You POSTed JSON that is not a JSON Object")
            case Some(jsObject) => jsObject.value.get("metadataSchema") match {
              case None => Right(MetadataSchema.empty)
              case Some(schemaJson) => Try(MetadataSchema.fromJson(schemaJson)) match {
                case Success(metadataSchema) => Right(metadataSchema)
                case Failure(ex) => Left("Your metadataSchema has the wrong format. It must have 'version' (1) and 'fields' (array of { name: 'foo', type: 'String' } Objects).")
              }
            }
          }
        }

        metadataSchemaOrError match {
          case Left(message) => Future.successful(BadRequest(message))
          case Right(metadataSchema) => {
            val attributesWithMetadata = attributes.copy(metadataSchema=metadataSchema)
            for {
              documentSet <- backend.create(attributesWithMetadata, request.apiToken.createdBy)
              apiToken <- apiTokenBackend.create(Some(documentSet.id), ApiToken.CreateAttributes(request.apiToken.createdBy, "[automatically returned when creating DocumentSet via API]"))
            } yield Created(Json.obj(
              "documentSet" -> Json.obj(
                "id" -> documentSet.id,
                "title" -> documentSet.title,
                "metadataSchema" -> documentSet.metadataSchema.toJson
              ),
              "apiToken" -> views.json.ApiToken.show(apiToken)
            ))
          }
        }
      }
    )
  }
}
