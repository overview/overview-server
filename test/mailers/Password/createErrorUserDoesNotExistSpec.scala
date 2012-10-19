package mailers.Password

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.i18n.Lang
import play.api.test.{FakeApplication,FakeRequest}

class createErrorUserDoesNotExistSpec extends Specification {
  step(start(FakeApplication()))

  trait OurContext extends Scope with Mockito {
    val email = "email@example.org"

    val lang = Lang("fu", "BA")
    val request = FakeRequest("POST", "https://example.org/password/create")

    lazy val mailer = createErrorUserDoesNotExist(email)(lang, request)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(email)))
    }
  }

  step(stop)
}
