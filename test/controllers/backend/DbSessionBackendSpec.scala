package controllers.backend

import java.util.{Date,UUID}
import java.sql.Timestamp

import models.{Session=>OSession,User} // beware database.Slick.simple.Session
import models.tables.{Sessions,Users}
import org.overviewproject.postgres.InetAddress

class DbSessionBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbSessionBackend {
      override val MaxSessionAgeInMs = 1000L
      override protected def minCreatedAt = new Timestamp(1000000000000L)
    }

    def insertUser(id: Long, email: String): User = {
      import org.overviewproject.database.Slick.simple._
      val ret = User(id=id, email=email)
      Users.insertInvoker.insert(ret)(session)
      ret
    }

    def insertSession(userId: Long, ip: String, createdAt: Date): OSession = {
      import org.overviewproject.database.Slick.simple._
      val ret = OSession(UUID.randomUUID,
        userId,
        InetAddress.getByName(ip),
        new Timestamp(createdAt.getTime),
        new Timestamp(createdAt.getTime)
      )
      Sessions.insertInvoker.insert(ret)(session)
      ret
    }

    def findSession(id: UUID): Option[OSession] = {
      import org.overviewproject.database.Slick.simple._
      Sessions.filter(_.id === id).firstOption(session)
    }
  }

  "#showWithUser" should {
    trait ShowWithUserScope extends BaseScope {
      val updatedAt1 = new java.util.Date(1000000000000L)
      val user1 = insertUser(123L, "user@example.org")
      val session1 = insertSession(user1.id, "192.168.0.1", updatedAt1)
    }

    "find the session with the user" in new ShowWithUserScope {
      val ret: Option[(OSession,User)] = await(backend.showWithUser(session1.id))
      ret.map(_._1) must beSome(session1)
      ret.map(_._2.copy(passwordHash="")) must beSome(user1)
    }

    "not find a different session" in new ShowWithUserScope {
      val session2 = insertSession(user1.id, "192.168.0.1", updatedAt1)
      val ret: Option[(OSession,User)] = await(backend.showWithUser(session1.id))
      ret.map(_._1.id) must beSome(session1.id)
    }

    "not find an expired session" in new ShowWithUserScope {
      val session2 = insertSession(user1.id, "192.168.0.1", new Date(999999999999L))
      await(backend.showWithUser(session2.id)) must beNone
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      val updatedAt1 = new java.util.Date(1000000000000L)
      val user1 = insertUser(123L, "user@example.org")
      val session1 = insertSession(user1.id, "192.168.0.1", updatedAt1)
    }

    "change the IP" in new UpdateScope {
      val attributes = OSession.UpdateAttributes("192.168.0.2", updatedAt1)
      await(backend.update(session1.id, attributes))
      findSession(session1.id).map(_.ip) must beSome(InetAddress.getByName("192.168.0.2"))
    }

    "change updatedAt" in new UpdateScope {
      val attributes = OSession.UpdateAttributes("192.168.0.1", new Date(1000000005000L))
      await(backend.update(session1.id, attributes))
      val s = findSession(session1.id)
      s.map(_.createdAt) must beSome(new Timestamp(1000000000000L))
      s.map(_.updatedAt) must beSome(new Timestamp(1000000005000L))
    }
  }
}
