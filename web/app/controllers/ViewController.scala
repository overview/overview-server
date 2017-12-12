package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsObject,JsNull,JsString,JsValue}
import play.api.mvc.{AnyContentAsJson,Result}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet,userOwningView}
import controllers.backend.{ApiTokenBackend,StoreBackend,ViewBackend}
import controllers.forms.{ViewForm,ViewUpdateAttributesForm}
import com.overviewdocs.models.{ApiToken,Tree,View,ViewDocumentDetailLink,ViewFilter}
import com.overviewdocs.models.tables.{Trees}

class ViewController @Inject() (
  storage: ViewController.Storage,
  apiTokenBackend: ApiTokenBackend,
  storeBackend: StoreBackend,
  viewBackend: ViewBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def indexJson(documentSetId: Long) = authorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    val trees = storage.findTrees(documentSetId).map(_.copy()).toArray

    viewBackend.index(documentSetId)
      .map((vs) => Ok(views.json.View.index(trees, vs)).withHeaders(CACHE_CONTROL -> "max-age=0"))
      .recover { case t: Throwable => InternalServerError(t.getMessage) }
  }

  def create(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val form = ViewForm.create(request.user.email)
    form.bindFromRequest.value match {
      case None => Future.successful(BadRequest("You must POST a 'title' and 'url'."))
      case Some(attributes) => {
        val result: Future[Result] = for {
          apiToken <- apiTokenBackend.create(
            Some(documentSetId),
            ApiToken.CreateAttributes(request.user.email, attributes.title)
          )
          view <- viewBackend.create(documentSetId, View.CreateAttributes(
            attributes.url,
            attributes.serverUrlFromPlugin,
            apiToken.token,
            attributes.title
          ))
        } yield Created(views.json.api.View.show(view))
        result.recover { case t: Throwable => BadRequest(t.getMessage) }
      }
    }
  }

  private def updateViewFilter(viewId: Long, filterJson: JsValue) = {
    def err(message: String) = Future.successful(BadRequest(jsonError("illegal-arguments", message)))

    filterJson match {
      case JsNull => {
        // unset the ViewFilter
        viewBackend.updateViewFilter(viewId, None).map(_ match {
          case Some(view) => Ok(views.json.View.show(view))
          case None => NotFound
        })
      }
      case JsObject(filterMap) => {
        filterMap.get("url") match {
          case Some(JsString(filterUrl)) => {
            viewBackend.updateViewFilter(viewId, Some(ViewFilter(filterUrl, JsObject(filterMap)))).map(_ match {
              case Some(view) => Ok(views.json.View.show(view))
              case None => NotFound
            })
          }
          case _ => err("Please include a 'filter.url' property, a String: as it is, your filter is invalid.")
        }
      }
      case _ => err("Please make the 'filter' property a JSON Object or null: not a String or Array or Number or Boolean.")
    }
  }

  private def updateDocumentDetailLink(viewId: Long, documentDetailLinkJson: JsValue) = {
    def err(message: String) = Future.successful(BadRequest(jsonError("illegal-arguments", message)))

    documentDetailLinkJson match {
      case JsNull => {
        // unset the DocumentDetailLink
        viewBackend.updateDocumentDetailLink(viewId, None).map(_ match {
          case Some(view) => Ok(views.json.View.show(view))
          case None => NotFound
        })
      }
      case JsObject(map) => {
        (map.get("url"), map.get("title"), map.get("text"), map.get("iconClass")) match {
          case (Some(JsString(url)), Some(JsString(title)), Some(JsString(text)), Some(JsString(iconClass))) => {
            if (url.replace(":documentId", "1") == url) {
              err("Your documentDetailLink.url must include ':documentId' somewhere: for instance, 'http://example.com/documents/:documentId/details")
            } else {
              val link = ViewDocumentDetailLink(url=url, title=title, text=text, iconClass=iconClass)
              viewBackend.updateDocumentDetailLink(viewId, Some(link)).map(_ match {
                case Some(view) => Ok(views.json.View.show(view))
                case None => NotFound
              })
            }
          }
          case _ => err("Invalid 'documentDetailLink' object. It needs 'url', 'title', 'text', and 'iconClass' properties: all Strings")
        }
      }
      case _ => err("Please make the 'documentDetailLink' property a JSON Object or null: not a String or Array or Number or Boolean.")
    }
  }

  def update(documentSetId: Long, viewId: Long) = authorizedAction(userOwningView(viewId)).async { implicit request =>
    request.body match {
      case AnyContentAsJson(requestJsValue) => {
        def err(message: String) = Future.successful(BadRequest(jsonError("illegal-arguments", message)))

        requestJsValue match {
          case JsObject(requestJson) => {
            (requestJson.get("filter"), requestJson.get("documentDetailLink")) match {
              case (Some(jsValue), None) => updateViewFilter(viewId, jsValue)
              case (None, Some(jsValue)) => updateDocumentDetailLink(viewId, jsValue)
              // We don't support both at once. No real reason; just that there's no spec.
              case (Some(_), Some(_)) => err("Please do not set both a 'filter' and a 'documentDetailLink'")
              case _ => err("Please include a 'filter' property or a 'documentDetailLink' property")
            }
          }
          case _ => err("If you're going to send JSON, please send a JSON Object. You sent something else.")
        }
      }
      case _ => ViewUpdateAttributesForm().bindFromRequest.fold(
        f => Future.successful(BadRequest),
        attributes => viewBackend.update(viewId, attributes).map(_ match {
          case Some(view) => Ok(views.json.View.show(view))
          case None => NotFound
        })
      )
    }
  }

  def destroy(documentSetId: Long, viewId: Long) = authorizedAction(userOwningView(viewId)).async { request =>
    viewBackend.show(viewId).flatMap(_ match {
      case Some(view) => {
        for {
          unit1 <- storeBackend.destroy(view.apiToken)
          unit2 <- viewBackend.destroy(view.id)
          unit3 <- apiTokenBackend.destroy(view.apiToken)
        } yield NoContent
      }
      case None => Future.successful(NotFound) // this is unlikely -- userOwningView() would normally fail
    })
  }
}

object ViewController {
  @ImplementedBy(classOf[ViewController.BlockingDatabaseStorage])
  trait Storage {
    def findTrees(documentSetId: Long) : Iterable[Tree]
  }

  class BlockingDatabaseStorage @Inject() extends Storage with HasBlockingDatabase {
    import database.api._

    override def findTrees(documentSetId: Long) = {
      blockingDatabase.seq(Trees.filter(_.documentSetId === documentSetId))
    }
  }
}
