package org.overviewproject.postgres

import org.postgresql.largeobject.LargeObjectManager
import org.postgresql.PGConnection

import org.overviewproject.test.{DbSpecification,SlickClientInSession}

class LargeObjectInputStreamSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    val data: Array[Byte] = "some contents".getBytes("ascii")
    val buffer = new Array[Byte](100)

    lazy val pgConnection = session.conn.unwrap(classOf[PGConnection])
    lazy val loApi = pgConnection.getLargeObjectAPI()

    lazy val loid = {
      val ret = loApi.createLO(LargeObjectManager.READ | LargeObjectManager.WRITE)
      val lo = loApi.open(ret, LargeObjectManager.WRITE)
      lo.write(data)
      lo.close
      ret
    }

    lazy val subject = new LargeObjectInputStream(loid, SlickClientInSession(session))
  }

  "read one byte at a time" in new BaseScope {
    override val data = "foo".getBytes("ascii")
    subject.read must beEqualTo("f".charAt(0))
    subject.read must beEqualTo("o".charAt(0))
    subject.read must beEqualTo("o".charAt(0))
    subject.read must beEqualTo(-1)
  }

  "read multiple bytes" in new BaseScope {
    override val data = "foo".getBytes("ascii")
    subject.read(buffer, 0, 2) must beEqualTo(2) // plus side-effect
    buffer(0) must beEqualTo("f".charAt(0))
    buffer(1) must beEqualTo("o".charAt(0))
    subject.read(buffer, 5, 2) must beEqualTo(1) // plus side-effect
    buffer(5) must beEqualTo("o".charAt(0))
    subject.read(buffer, 0, 1) must beEqualTo(-1)
  }

  "throw IOException if the object disappears" in new BaseScope {
    subject // initialize lazy variables
    loApi.delete(loid)
    subject.read must throwA[java.io.IOException]
    subject.read(buffer, 0, 1) must throwA[java.io.IOException]
  }
}
