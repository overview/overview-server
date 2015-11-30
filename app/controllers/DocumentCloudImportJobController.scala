package controllers

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{DocumentSet,DocumentCloudImport}
import com.overviewdocs.models.tables.DocumentCloudImports
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.auth.AuthorizedAction
import controllers.backend.DocumentSetBackend
import controllers.forms.DocumentCloudImportJobForm
import controllers.util.JobQueueSender

trait DocumentCloudImportJobController extends Controller {
  protected val documentSetBackend: DocumentSetBackend
  protected val storage: DocumentCloudImportJobController.Storage
  protected val jobQueueSender: JobQueueSender

  def _new(query: String) = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.DocumentCloudImportJob._new(request.user, query))
  }

  def create() = AuthorizedAction(anyUser).async { implicit request =>
    DocumentCloudImportJobForm().bindFromRequest().fold(
      f => Future.successful(BadRequest),
      record => {
        for {
          documentSet <- documentSetBackend.create(DocumentSet.CreateAttributes(
            title=record.title
          ), request.user.email)
          dcImport <- storage.insertImport(DocumentCloudImport.CreateAttributes(
            documentSetId=documentSet.id,
            query=record.query,
            lang=record.lang,
            username=record.username,
            password=record.password,
            splitPages=record.splitPages
          ))
        } yield {
          jobQueueSender.send(DocumentSetCommands.AddDocumentsFromDocumentCloud(dcImport))
          Redirect(routes.DocumentSetController.show(documentSet.id)).flashing("event" -> "document-set-create")
        }
      }
    )
  }

  def delete(documentSetId: Long, dciId: Int) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    for {
      _ <- storage.cancelImport(documentSetId, dciId)
    } yield NoContent
  }
}

object DocumentCloudImportJobController extends DocumentCloudImportJobController {
  override protected val documentSetBackend = DocumentSetBackend
  override protected val jobQueueSender = JobQueueSender

  trait Storage {
    def insertImport(attributes: DocumentCloudImport.CreateAttributes): Future[DocumentCloudImport]
    def cancelImport(documentSetId: Long, dciId: Int): Future[Unit]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    lazy val inserter = DocumentCloudImports.map(_.createAttributes).returning(DocumentCloudImports)
    lazy val canceller = Compiled { (documentSetId: Rep[Long], importId: Rep[Int]) =>
      DocumentCloudImports
        .filter(_.documentSetId === documentSetId)
        .filter(_.id === importId)
        .map(_.cancelled)
    }

    override def insertImport(attributes: DocumentCloudImport.CreateAttributes) = {
      database.run(inserter.+=(attributes))
    }

    override def cancelImport(documentSetId: Long, dciId: Int) = {
      database.runUnit(canceller(documentSetId, dciId).update(true))
    }
  }

  override protected val storage = DatabaseStorage
}
