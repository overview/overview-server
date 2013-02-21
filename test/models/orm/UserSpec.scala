package models.orm

import java.sql.Timestamp
import org.joda.time.DateTime.now
import org.specs2.mutable.Specification
import org.overviewproject.postgres.SquerylEntrypoint._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

import helpers.DbTestContext
import models.orm.DocumentSetType._

class UserSpec extends Specification {
  trait UserContext extends DbTestContext {
    val query = "query"
    val user = User()
  }

  step(start(FakeApplication()))

  "User" should {

    inExample("Create a DocumentSet") in new UserContext {
      Schema.users.insert(user)

      val documentSet = user.createDocumentSet(query)

      documentSet.users.iterator.toSeq must haveTheSameElementsAs(Seq(user))

      val documentSetUserLink = Schema.documentSetUsers.where(dsu =>
        dsu.documentSetId === documentSet.id and dsu.userEmail === user.email).headOption

      documentSetUserLink must beSome
    }

    "Not create a DocumentSet if not inserted in database" in new UserContext {
      user.createDocumentSet(query) must throwAn[IllegalArgumentException]
    }

    "order document sets from oldest to newest" in new UserContext {
      val user2 = Schema.users.insert(user)
      val documentSet1 = DocumentSet(DocumentCloudDocumentSet, 0L, title = "earliest", query = Some("early"), createdAt = new Timestamp(1351519451289L)).save
      val documentSet2 = DocumentSet(DocumentCloudDocumentSet, 0L, title = "later", query = Some("late"), createdAt = new Timestamp(1351519465652L)).save
      Schema.documentSetUsers.insert(Seq(
        DocumentSetUser(documentSet1.id, user2.email),
        DocumentSetUser(documentSet2.id, user2.email)))

      val user3 = User.findById(user2.id).get

      val documentSets = user3.orderedDocumentSets
      documentSets.size must be equalTo (2)
      documentSets.head.title must be equalTo ("later")
    }

    "throw an exception if confirmation token is not unique" in new UserContext {
      val token = Some("a token")
      val user1 = User(email = "user@example.org", confirmationToken = token)
      val user2 = User(email = "user2@example.org", confirmationToken = token)

      user1.save
      user2.save must throwA[java.lang.RuntimeException]
    }

    "return users sorted by last activity date descending, with nulls last" in new DbTestContext {
      val activeUsers = Seq.tabulate(3)(i => User(email = "user " + i, lastActivityAt = Some(new Timestamp(1000 - i))))
      val confirmedUsers = Seq.tabulate(3)(i => User(email = "user " + (10 + i), confirmedAt = Some(new Timestamp(1000 - i))))

      Schema.users.insert(confirmedUsers ++ activeUsers)
      val users = User.all.filterNot(_.email != "admin@overviewproject.org")

      users.map(_.email).toSeq must beSorted

    }
  }

  step(stop)
}
