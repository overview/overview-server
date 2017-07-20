package mailers

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.i18n.Messages

import com.overviewdocs.models.UserRole
import controllers.util.NullMessagesApi
import models.{User=>UserModel}

class MailSpecification  extends Specification {
  trait MailScope extends Scope {
    val factory = com.overviewdocs.test.factories.PodoFactory
    def fakeUser: UserModel = UserModel(email="user@example.org", role=UserRole.NormalUser)

    implicit def messages: Messages = NullMessagesApi.messages

    def mail: Mail
  }
}
