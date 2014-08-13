package models.orm.finders

import models.Session

class SessionFinderSpec extends FinderSpecification {
  "with one session we want and one we don't" should {
    trait TwoSessionScope extends FinderScope {
      val user2 = schema.users.insert(models.User())
      val existingSession = schema.sessions.insert(Session(1L, "127.0.0.1"))
      val wrongSession = schema.sessions.insert(Session(user2.id, "127.0.0.1"))
    }

    "find the session by UUID" in new TwoSessionScope {
      val foundSessions = SessionFinder.byId(existingSession.id)
      foundSessions.count must beEqualTo(1)
      foundSessions.headOption.map(_.id) must beSome(existingSession.id)
    }

    "find the session by user ID" in new TwoSessionScope {
      val foundSessions = SessionFinder.byUserId(1L)
      foundSessions.count must beEqualTo(1)
      foundSessions.headOption.map(_.id) must beSome(existingSession.id)
    }
  }

  "with an expired session" should {
    trait ExpiredSessionScope extends FinderScope {
      val expiredSession = schema.sessions.insert(
        Session(1L, "127.0.0.1")
          .copy(createdAt=new java.sql.Timestamp(new java.util.Date().getTime() - 31L * 86400 * 1000))
      )
      val freshSession = schema.sessions.insert(Session(1L, "127.0.0.1"))
    }

    "find the expired session by default" in new ExpiredSessionScope {
      val foundSessions = SessionFinder.byUserId(1L)
      foundSessions.count must beEqualTo(2)
    }

    "find only the expired session with .expired" in new ExpiredSessionScope {
      val foundSessions = SessionFinder.byUserId(1L).expired
      foundSessions.count must beEqualTo(1)
      foundSessions.headOption.map(_.id) must beSome(expiredSession.id)
    }

    "find only the not-expired session with .notExpired" in new ExpiredSessionScope {
      val foundSessions = SessionFinder.byUserId(1L).notExpired
      foundSessions.count must beEqualTo(1)
      foundSessions.headOption.map(_.id) must beSome(freshSession.id)
    }
  }
}
