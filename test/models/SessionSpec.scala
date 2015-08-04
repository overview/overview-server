package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.util.Date

import com.overviewdocs.postgres.InetAddress

class SessionSpec extends Specification {
  trait BaseScope extends Scope {
    val userId = 4L
    val ip = InetAddress.getByName("192.168.1.1")

    def generateSession = Session(userId=userId, ip=ip)

    lazy val session = generateSession
  }

  "Session" should {
    "use InetAddress.getByName() in ctor" in new Scope {
      Session(1L, "192.168.1.1").ip.getHostAddress must beEqualTo("192.168.1.1")
    }

    "generate a unique UUID" in new BaseScope {
      session
      val session2 = generateSession
      session.id mustNotEqual(session2.id)
    }

    "use the supplied userId" in new BaseScope {
      session.userId must beEqualTo(userId)
    }

    "use the supplied ip" in new BaseScope {
      session.ip must beEqualTo(ip)
    }

    "set a default createdAt" in new BaseScope {
      val d = session.createdAt.getTime() - (new Date()).getTime()
      d must beLessThan(1000L) // the test should take 1ms, so 1000ms is fine
    }

    "set updatedAt=createdAt" in new BaseScope {
      session.updatedAt must beEqualTo(session.createdAt)
    }

    "update updatedAt" in new BaseScope {
      val time1 = session.updatedAt
      val time2 = session.update(ip).updatedAt
      time2 must not be(time1)
    }

    "update ip" in new BaseScope {
      val ip2 = InetAddress.getByName("192.168.1.103")
      session.update(ip2).ip.getHostAddress must beEqualTo("192.168.1.103")
    }

    "update ip from String" in new BaseScope {
      session.update("192.168.1.103").ip.getHostAddress must beEqualTo("192.168.1.103")
    }

    "update from attributes" in new BaseScope {
      val ip2 = "192.168.1.103"
      val date2 = new Date(1425306564964L)
      val timestamp2 = new java.sql.Timestamp(1425306564964L)
      val session2 = session.update(Session.UpdateAttributes(ip2, date2))
      session2.ip.getHostAddress must beEqualTo(ip2)
      session2.updatedAt must beEqualTo(timestamp2)
      session2.createdAt must not(beEqualTo(timestamp2))
    }
  }
}
