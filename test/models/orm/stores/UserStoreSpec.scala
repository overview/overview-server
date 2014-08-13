package models.orm.stores

import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

import helpers.DbTestContext
import models.User

class UserStoreSpec extends Specification {
  step(start(FakeApplication()))

  "UserStore" should {
    "throw an exception if confirmation token is not unique" in new DbTestContext {
      def insertWithConstantToken(email: String) = {
        UserStore.insertOrUpdate(User(email=email, confirmationToken=Some("a token")))
      }

      insertWithConstantToken("user@example.org")
      insertWithConstantToken("user2@example.org") must throwA[java.lang.RuntimeException]
    }
  }

  step(stop)
}
