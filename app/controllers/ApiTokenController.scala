package controllers

import play.api.mvc.{Action,Controller}
import play.api.data.{Form,Forms}

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.userOwningDocumentSet
import models.User
import models.OverviewDatabase
import org.overviewproject.models.ApiToken

trait ApiTokenController extends Controller {
  protected val storage : ApiTokenController.Storage

  def index(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    render {
      case Accepts.Html() => {
        Ok(views.html.ApiToken.index(request.user, id))
      }
      case Accepts.Json() => {
        val tokens = storage.getTokens(request.user.email, id)
        Ok(views.json.ApiToken.index(tokens))
      }
    }
  }

  def create(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val form = Form("description" -> Forms.nonEmptyText)
    val description = form.bindFromRequest().fold(
      f => "",
      f => f
    )
    val token = storage.createToken(request.user.email, id, description)
    Ok(views.json.ApiToken.show(token))
  }

  /** Destroys the token.
    *
    * We don't need auth: if somebody _has_ the token, then that person is
    * authenticated by definition. Skipping auth here can only benefit the
    * legitimate owner of a token, by deleting his/her leaked token.
    */
  def destroy(id: Long, token: String) = Action { request =>
    storage.destroyToken(token)
    NoContent
  }
}

object ApiTokenController extends ApiTokenController {
  trait Storage {
    def getTokens(email: String, documentSetId: Long) : Seq[ApiToken]
    def createToken(email: String, documentSetId: Long, description: String) : ApiToken
    def destroyToken(token: String) : Unit
  }

  override val storage = new Storage {
    override def getTokens(email: String, documentSetId: Long) = {
      OverviewDatabase.inTransaction {
        import models.orm.Schema
        import org.overviewproject.postgres.SquerylEntrypoint._

        from(Schema.apiTokens)(t =>
          where(t.createdBy === email and t.documentSetId === documentSetId)
          select(t)
        ).map(_.copy()).toSeq
      }
    }

    override def createToken(email: String, documentSetId: Long, description: String) = {
      val token = ApiToken.generate(email, documentSetId, description)
      OverviewDatabase.inTransaction {
        import models.orm.Schema
        Schema.apiTokens.insert(token)
      }
    }

    override def destroyToken(token: String) = {
      OverviewDatabase.inTransaction {
        import models.orm.Schema
        import models.orm.Schema.ApiTokenKED
        import org.overviewproject.postgres.SquerylEntrypoint._

        Schema.apiTokens.delete(token)
      }
    }
  }
}
