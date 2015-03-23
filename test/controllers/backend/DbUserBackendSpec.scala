package controllers.backend

import java.util.{Date,UUID}
import java.sql.Timestamp

import models.User
import models.tables.Users

class DbUserBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbUserBackend

    def insertUser(id: Long, email: String): User = {
      import org.overviewproject.database.Slick.simple._
      val ret = User(id=id, email=email)
      Users.insertInvoker.insert(ret)(session)
      ret
    }

    def findUser(id: Long): Option[User] = {
      import org.overviewproject.database.Slick.simple._
      Users.filter(_.id === id).firstOption(session)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val user = insertUser(123L, "user-123@example.org")
    }

    "destroy a normal user" in new DestroyScope {
      await(backend.destroy(user.id)) must beEqualTo(())
      findUser(user.id) must beNone
    }

    "not destroy a nonexistent user" in new DestroyScope {
      await(backend.destroy(122L)) must beEqualTo(())
      findUser(user.id) must beSome
    }
  }

  "#updateLastActivity" should {
    trait UpdateLastActivityScope extends BaseScope {
      val user = insertUser(123L, "user@example.org")
    }

    "change lastActivityIp" in new UpdateLastActivityScope {
      await(backend.updateLastActivity(user.id, "192.168.0.1", new Timestamp(1425318194284L)))
      findUser(user.id).flatMap(_.lastActivityIp) must beSome("192.168.0.1")
    }

    "change lastActivityAt" in new UpdateLastActivityScope {
      await(backend.updateLastActivity(user.id, "192.168.0.1", new Timestamp(1425318194284L)))
      findUser(user.id).flatMap(_.lastActivityAt) must beSome(new Timestamp(1425318194284L))
    }
  }
}
