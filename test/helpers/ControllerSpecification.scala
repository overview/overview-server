package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import play.api.mvc.AnyContent
import play.api.test.{FakeApplication, FakeRequest}
import play.api.Play.{start,stop}

import controllers.auth.{AuthorizedRequest, OptionallyAuthorizedRequest}
import models.OverviewUser

/** A test environment for controllers.
  */
trait ControllerSpecification extends Specification with Mockito {
  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }

  def fakeUser : OverviewUser = {
    val ret = mock[OverviewUser]
    ret.email returns "user@example.org"
    ret.isAdministrator returns false
    ret
  }
  def fakeRequest = FakeRequest()
  def fakeAuthorizedRequest(user: OverviewUser) : AuthorizedRequest[AnyContent] = new AuthorizedRequest(fakeRequest, user)
  def fakeAuthorizedRequest() : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser)
  def fakeOptionallyAuthorizedRequest(user: Option[OverviewUser]) = new OptionallyAuthorizedRequest(fakeRequest, user)

  implicit val timeout = akka.util.Timeout(999999)

  val h = play.api.test.Helpers // rrgh, this should be a trait
}
