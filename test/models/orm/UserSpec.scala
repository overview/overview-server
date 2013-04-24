package models.orm

import java.sql.Timestamp
import org.joda.time.DateTime.now
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

import org.overviewproject.postgres.SquerylEntrypoint._
import helpers.DbTestContext

class UserSpec extends Specification {
  step(start(FakeApplication()))

  "User" should {
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
