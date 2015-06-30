package mailers

import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Scope, Step}
import play.api.Play.{start,stop}
import play.api.i18n.{Lang,Messages}
import play.api.mvc.{Flash,RequestHeader}
import play.api.test.{FakeApplication,FakeRequest}

import models.{User=>UserModel}
import org.overviewproject.models.UserRole

class MailerSpecification extends test.helpers.InAppSpecification {
  // Need fake application, because some views show CSRF tokens
  override def map(fs: => Fragments) = {
    val app = FakeApplication()
    Step(start(app)) ^ super.map(fs) ^ Step(stop(app))
  }

  trait MailerScope extends Scope {
    val factory = org.overviewproject.test.factories.PodoFactory
    def fakeUser: UserModel = UserModel(email="user@example.org", role=UserRole.NormalUser)

    implicit def request: RequestHeader = FakeRequest()
    implicit val messages: Messages = new Messages(Lang("en"), new test.helpers.MockMessagesApi())

    def mailer: Mailer
  }
}
