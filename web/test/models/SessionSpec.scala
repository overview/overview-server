package models

import org.specs2.mutable.Specification

class SessionSpec extends Specification {
  "Session" should {
    "generate a unique UUID" in {
      val session1 = Session(1L, "192.168.1.1")
      val session2 = Session(1L, "192.168.1.1")
      session1.id mustNotEqual(session2.id)
    }

    "use the supplied userId" in {
      Session(1L, "192.168.1.1").userId must beEqualTo(1L)
    }

    "use the supplied IP" in {
      Session(1L, "192.168.1.1").ip.value must beEqualTo("192.168.1.1")
    }

    "set a default createdAt" in {
      Session(1L, "192.168.1.1").createdAt.getTime must beCloseTo(new java.util.Date().getTime, 1000L)
    }

    "set updatedAt=createdAt" in {
      val session = Session(1L, "192.168.1.1")
      session.updatedAt must beEqualTo(session.createdAt)
    }

    "update updatedAt" in {
      val session = Session(1L, "192.168.1.1").copy(updatedAt=new java.sql.Timestamp(0L))
      session.update("192.168.1.1").updatedAt.getTime must beCloseTo(new java.util.Date().getTime, 1000L)
    }

    "update ip" in {
      Session(1L, "192.168.1.1").update("192.168.1.2").ip.value must beEqualTo("192.168.1.2")
    }

    "update from attributes" in {
      val date2 = new java.sql.Timestamp(1425306564964L)
      val session = Session(1L, "192.168.1.1")
      val session2 = session.update(Session.UpdateAttributes("192.168.1.2", date2))
      session2.ip.value must beEqualTo("192.168.1.2")
      session2.updatedAt must beEqualTo(date2)
      session2.createdAt must not(beEqualTo(date2))
    }
  }
}
