package models.orm

import helpers.DbTestContext
import java.sql.Timestamp
import org.joda.time.DateTime.now
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

class UserSpec extends Specification {
  trait UserContext extends DbTestContext {
    val query = "query"
    val user = User()
  }

  step(start(FakeApplication()))

  "User" should {

    "Create a DocumentSet" in new UserContext {
      Schema.users.insert(user)

      val documentSet = user.createDocumentSet(query)

      documentSet.users must haveTheSameElementsAs(Seq(user))

      val documentSetUserLink = Schema.documentSetUsers.where(dsu => 
        dsu.documentSetId === documentSet.id and dsu.userId === user.id
      ).headOption

      documentSetUserLink must beSome
    }

    "Not create a DocumentSet if not inserted in database" in new UserContext {
      user.createDocumentSet(query) must throwAn[IllegalArgumentException]
    }

    inExample("throw an exception if confirmation token is not unique") in new UserContext{
      val token = Some("a token")
      val user1 = User(email="user@example.org", confirmationToken=token)
      val user2 = User(email="user2@example.org", confirmationToken=token)

      user1.save
      user2.save must throwA[java.lang.RuntimeException]
    }
  }

  step(stop)
}
