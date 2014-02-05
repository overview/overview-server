package controllers.auth

import play.api.Play
import play.api.Play.current
import play.api.mvc.{RequestHeader,SimpleResult,Results}
import scala.util.control.Exception.catching

import models.OverviewUser

/** Loads OverviewUser objects from the database.
  *
  * Usage:
  *
  *    val request = FakeRequest()
  *    val authority = Authorities.userOwningDocumentSet(4L)
  *    val errorOrUser = loadUser(request, authority)
  *
  * In this example, errorOrUser will be a Right[OverviewUser] if:
  *
  * * The request has a session (cookie)
  * * The session contains a user ID
  * * The user ID corresponds to a user in the database
  * * The given user is authorized according to the given authority
  *
  * If any of these conditions aren't satisfied, UserFactory will return an
  * appropriate Left[SimpleResult] instead.
  *
  * This object does not require that requests be over HTTPS.
  */
trait UserFactory {
  private[auth] val UserIdKey = AuthResults.UserIdKey // TODO find a sensible class for this constant

  /** Returns either a SimpleResult (no access) or an OverviewUser (access) recorded in the database. */
  def loadUser(request: RequestHeader, authority: Authority) : Either[SimpleResult, OverviewUser] = {
    recordedUserIfAuthorized(request, authority)
  }

  /** Returns the user from the database, as specified in the request session. */
  private def loadUserFromDatabase(request: RequestHeader) : Option[OverviewUser] = {
    if (isMultiUser) {
      for {
        userIdString <- request.session.get(UserIdKey)
        userId <- catching(classOf[NumberFormatException]).opt(userIdString.toLong)
        user <- userIdToUser(userId)
      } yield user
    } else {
      userIdToUser(1L).map(_.asNormalUser)
    }
  }

  /** Returns the user from the database, as specified by ID. */
  protected def userIdToUser(id: Long) : Option[OverviewUser]

  /** true if we're using a multi-user system, with auth. */
  protected val isMultiUser : Boolean

  /** Returns a Right[OverviewUser] if the user exists. */
  private def userIfAuthenticated(request: RequestHeader) = {
    loadUserFromDatabase(request).toRight(AuthResults.authenticationFailed(request))
  }

  /** Returns a Right[OverviewUser] if the user is authorized under the authority. */
  private def userIfAuthorized(request: RequestHeader, user: OverviewUser, authority: Authority) = {
    Either.cond(authority(user), user, AuthResults.authorizationFailed(request))
  }

  /** Returns a recorded version of the same user. */
  private def recordedUser(request: RequestHeader, user: OverviewUser) = {
    user.withActivityRecorded(request.remoteAddress, new java.util.Date()).save
  }

  /** Returns a Right[OverviewUser], saved to the DB, if the user is authenticated and authorized. */
  private def recordedUserIfAuthorized(request: RequestHeader, authority: Authority) = {
    userIfAuthenticated(request)
      .right.flatMap(userIfAuthorized(request, _, authority))
      .right.map(recordedUser(request, _))
  }
}

object UserFactory extends UserFactory {
  override protected def userIdToUser(id: Long) = OverviewUser.findById(id)

  override protected lazy val isMultiUser = {
    Play.configuration.getBoolean("overview.multi_user").getOrElse(true)
  }
}
