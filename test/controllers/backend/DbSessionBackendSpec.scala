package controllers.backend

import java.util.{Date,UUID}
import java.sql.Timestamp

import models.{Session=>OSession,User} // beware database.Slick.simple.Session
import models.tables.{Sessions,Users}
import org.overviewproject.postgres.InetAddress

class DbSessionBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    import databaseApi._

    val backend = new DbSessionBackend with org.overviewproject.database.DatabaseProvider {
      override val MaxSessionAgeInMs = 1000L
      override protected def minCreatedAt = new Timestamp(1000000000000L)
    }

    def insertUser(id: Long, email: String): User = {
      val ret = User(id=id, email=email)
      blockingDatabase.runUnit(Users.+=(ret))
      ret
    }

    def insertSession(userId: Long, ip: String, createdAt: Date): OSession = {
      val ret = OSession(UUID.randomUUID,
        userId,
        InetAddress.getByName(ip),
        new Timestamp(createdAt.getTime),
        new Timestamp(createdAt.getTime)
      )
      blockingDatabase.runUnit(Sessions.+=(ret))
      ret
    }

    def findSession(id: UUID): Option[OSession] = {
      blockingDatabase.option(Sessions.filter(_.id === id))
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

  "#create" should {
    trait CreateScope extends BaseScope {
      val user1 = insertUser(123L, "user@example.org")
    }

    "create a Session in the database" in new CreateScope {
      val ret = await(backend.create(user1.id, "127.0.0.1"));
      val dbRet = findSession(ret.id)
      dbRet must beSome(ret)
      ret.userId must beEqualTo(123L)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val updatedAt1 = new java.util.Date(1000000000000L)
      val user1 = insertUser(123L, "user@example.org")
      val session1 = insertSession(user1.id, "192.168.0.1", updatedAt1)
    }

    "destroy a Session" in new DestroyScope {
      await(backend.destroy(session1.id))
      findSession(session1.id) must beNone
    }

    "not destroy other Sessions" in new DestroyScope {
      val session2 = insertSession(user1.id, "192.168.0.2", updatedAt1)
      await(backend.destroy(session1.id))
      findSession(session2.id) must beSome
    }
  }

  "#destroyExpiredSessionsForUserId" should {
    trait DestroyExpiredScope extends BaseScope {
      val nDays = 50
      val recentDate = new java.util.Date(1000000000001L)
      val oldDate = new java.util.Date(999999999999L)
      val user1 = insertUser(123L, "user@example.org")

      def go = await(backend.destroyExpiredSessionsForUserId(user1.id))
    }

    "destroy old Sessions" in new DestroyExpiredScope {
      val oldSession = insertSession(user1.id, "192.168.0.2", oldDate)
      go
      findSession(oldSession.id) must beNone
    }

    "not destroy new Sessions" in new DestroyExpiredScope {
      val recentSession = insertSession(user1.id, "192.168.0.1", recentDate)
      go
      findSession(recentSession.id) must beSome
    }

    "not destroy other Users' Sessions" in new DestroyExpiredScope {
      val user2 = insertUser(124L, "user2@example.org")
      val otherSession = insertSession(user2.id, "192.168.0.3", oldDate)
      go
      findSession(otherSession.id) must beSome
    }
  }
}
