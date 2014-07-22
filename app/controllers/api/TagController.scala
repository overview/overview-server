package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import org.overviewproject.tree.orm.Tag

trait TagController extends Controller {
  protected val storage: TagController.Storage

  def index(documentSetId: Long) = ApiAuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    for (tags <- storage.index(documentSetId))
      yield Ok(views.json.api.Tag.index(tags))
  }
}

object TagController extends TagController {
  trait Storage {
    /** Retries a list of Tags for the given DocumentSet */
    def index(documentSetId: Long): Future[Seq[Tag]]
  }

  override protected val storage = new Storage {
    import models.OverviewDatabase
    import models.orm.finders.TagFinder

    override def index(documentSetId: Long) = Future {
      OverviewDatabase.inTransaction {
        TagFinder.byDocumentSet(documentSetId).map(_.copy()).toSeq
      }
    }
  }
}
