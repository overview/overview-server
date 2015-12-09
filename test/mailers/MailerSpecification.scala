package mailers

import org.specs2.specification.Scope
import play.api.i18n.{Lang,Messages}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import models.{User=>UserModel}
import com.overviewdocs.models.UserRole

class MailerSpecification extends test.helpers.InAppSpecification {
  trait MailerScope extends Scope {
    val factory = com.overviewdocs.test.factories.PodoFactory
    def fakeUser: UserModel = UserModel(email="user@example.org", role=UserRole.NormalUser)

    implicit def request: RequestHeader = FakeRequest()
    implicit val messages: Messages = new Messages(Lang("en"), new test.helpers.MockMessagesApi())

    def mailer: Mailer
  }
}
