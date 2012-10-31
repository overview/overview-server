package controllers

import java.util.UUID
import models.orm.SquerylPostgreSqlAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import play.api.Play.current
import play.api.db.DB
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{Action, BodyParser, BodyParsers}

object UploadController extends BaseController {

  def authenticatedBodyParser[A](p: BodyParser[A]) = parse.using { implicit request =>
    DB.withTransaction { implicit connection =>
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) { // sets thread-local variable
	authorized(anyUser) match {
	  case Left(e) => parse.error(e)
	  case Right(u) => p
	}
      }
    }
  }

  def show(uuid: UUID) = Action(BodyParsers.parse.anyContent) { request =>
    Ok("ok")
  }

  def create(uuid: UUID) = Action(authenticatedBodyParser(fileUpload)) { request =>
    println("Total: " + request.body)
    Ok("ok")
  }

  def fileUpload: BodyParser[Long] = BodyParser("File upload") { request =>
    Iteratee.fold[Array[Byte], Long](0l)((count, bytes) => {
      println("count: " + count + " + " + bytes.size)
      count + bytes.size
    }).mapDone { total => Right(total) }
  }
}
