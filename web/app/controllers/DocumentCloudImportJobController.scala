package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi
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

class DocumentCloudImportJobController @Inject() (
  documentSetBackend: DocumentSetBackend,
  storage: DocumentCloudImportJobController.Storage,
  jobQueueSender: JobQueueSender,
  val controllerComponents: ControllerComponents,
  documentCloudImportJobNewHtml: views.html.DocumentCloudImportJob._new
) extends BaseController {
  def _new(query: String) = authorizedAction(anyUser) { implicit request =>
    Ok(documentCloudImportJobNewHtml(request.user, query))
  }

  def create() = authorizedAction(anyUser).async { implicit request =>
    DocumentCloudImportJobForm().bindFromRequest().fold(
      f => Future.successful(BadRequest),
      record => {
        for {
          documentSet <- documentSetBackend.create(DocumentSet.CreateAttributes(
            title=record.title,
            query=Some(record.query)
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

  def delete(documentSetId: Long, dciId: Int) = authorizedAction(userOwningDocumentSet(documentSetId)).async {
    for {
      _ <- storage.cancelImport(documentSetId, dciId)
    } yield NoContent
  }
}

object DocumentCloudImportJobController {
  @ImplementedBy(classOf[DocumentCloudImportJobController.DatabaseStorage])
  trait Storage {
    def insertImport(attributes: DocumentCloudImport.CreateAttributes): Future[DocumentCloudImport]
    def cancelImport(documentSetId: Long, dciId: Int): Future[Unit]
  }

  class DatabaseStorage @Inject() extends Storage with HasDatabase {
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
}
