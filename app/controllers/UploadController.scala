package controllers

import java.sql.Connection
import java.util.UUID
import models.orm.SquerylPostgreSqlAdapter
import models.upload.OverviewUpload
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import play.api.Play.current
import play.api.db.DB
import play.api.libs.iteratee.Error
  import play.api.libs.iteratee.Input
import play.api.mvc.{ Action, BodyParser, BodyParsers, Request, RequestHeader }
import scalax.io.Input

object UploadController extends BaseController {

  protected def authorizeInTransaction(authority: Authority)(implicit r: RequestHeader) = {
    DB.withTransaction { implicit connection =>
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) { // sets thread-local variable
        authorized(authority)
      }
    }
  }

  def authorizedBodyParser[A](authority: Authority)(f: User => BodyParser[A]) = parse.using { implicit request =>
    authorizeInTransaction(authority) match {
      case Left(e) => parse.error(e)
      case Right(user) => f(user)
    }
  }

  def show(uuid: UUID) = Action(BodyParsers.parse.anyContent) { request =>
    Ok("ok")
  }

  def create(uuid: UUID) = ActionInTransaction(authorizedFileUploadBodyParser(uuid)) { (request: Request[Option[OverviewUpload]], connection: Connection) =>
    println("Total: " + request.body)
    Ok("ok")
  }

  
  
  def authorizedFileUploadBodyParser(uuid: UUID) = authorizedBodyParser(anyUser) { user => fileUploadBodyParser(user, uuid) }

  def fileUploadBodyParser(user: User, guid: UUID): BodyParser[Option[OverviewUpload]] = BodyParser("File upload") { request => fileInfoFromHeader(request) match {
	case Some((filename, contentLength)) =>
    FileUploadIteratee.store(user.id, guid, filename, contentLength) mapDone { upload => Right(upload) }
      case None => Error("Bad header", Input.EOF)
  }
  }

  private def fileInfoFromHeader(header: RequestHeader): Option[(String, Long)] = {
    for {
      contentDisposition <- header.headers.get("CONTENT-DISPOSITION")
      contentLength <- header.headers.get("CONTENT-LENGTH")
    } yield {
      val disposition = "[^=]*=\"?([^\"]*)\"?".r // attachment ; filename="foo.bar" (optional quotes)
      val disposition(filename) = contentDisposition
      (filename, contentLength.toLong)
    }
  }
  
}
