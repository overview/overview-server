package controllers.util

import java.util.UUID
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{BodyParser,RequestHeader,Result,Results}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.{Future,blocking}
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.auth.{Authorities,SessionFactory}
import models.OverviewDatabase
import models.{Session,User}

trait MassUploadBodyParser extends BodyParser[Unit] {
  val guid: UUID
}

class UserMassUploadBodyParser(userEmail: String, override val guid: UUID) extends MassUploadBodyParser {
  override def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, Unit]] = {
    MassUploadFileIteratee(userEmail, request, guid)
      .map(_ match {
        case MassUploadFileIteratee.Ok => Right(Unit)
        case MassUploadFileIteratee.BadRequest(message) => Left(Results.BadRequest(message))
      })
  }

  override def toString = s"UserMassUploadBodyParser(${userEmail}, ${guid})"
}

class AuthorizedMassUploadBodyParser(sessionFactory: SessionFactory, override val guid: UUID) extends MassUploadBodyParser {
  def this(guid: UUID) = this(SessionFactory, guid)

  override def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, Unit]] = {
    val maybeSession: Either[Result, (Session,User)] = blocking {
      OverviewDatabase.inTransaction {
        sessionFactory.loadAuthorizedSession(request, Authorities.anyUser)
      }
    }
    maybeSession match {
      case Left(e) => Iteratee.ignore.map(_ => Left(e))
      case Right((session: Session, user: User)) => new UserMassUploadBodyParser(user.email, guid)(request)
    }
  }
}
