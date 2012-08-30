package mailers.User

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.i18n.Lang
import play.api.test.{FakeApplication,FakeRequest}

class createErrorUserAlreadyExistsSpec extends Specification {
  step(start(FakeApplication()))

  trait OurContext extends Scope {
    val user = new models.orm.User(email = "email@example.org", passwordHash = "hash")
    val lang = Lang("fu", "BA")
    val request = FakeRequest("POST", "https://example.org/user/create")

    lazy val mailer = createErrorUserAlreadyExists(user)(lang, request)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }
  }

  step(stop)
}
