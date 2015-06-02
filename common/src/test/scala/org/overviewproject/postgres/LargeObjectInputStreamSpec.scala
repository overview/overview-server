package org.overviewproject.postgres

import org.postgresql.largeobject.LargeObjectManager
import org.postgresql.PGConnection

import org.overviewproject.test.{DbSpecification,SlickClientInSession}

class LargeObjectInputStreamSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    connection.setAutoCommit(false)
    val loApi = pgConnection.getLargeObjectAPI()

    def lo(loData: Array[Byte]): LargeObjectInputStream = {
      val loid = loApi.createLO(LargeObjectManager.READ | LargeObjectManager.WRITE)
      val lo = loApi.open(loid, LargeObjectManager.WRITE)
      lo.write(loData)
      lo.close
      connection.commit() // FIXME leak -- we should clear LargeObjects on test suite start
      new LargeObjectInputStream(loid, new SlickClientInSession {})
    }
  }

  "read one byte at a time" in new BaseScope {
    val subject = lo("foo".getBytes("ascii"))
    subject.read must beEqualTo("f".charAt(0))
    subject.read must beEqualTo("o".charAt(0))
    subject.read must beEqualTo("o".charAt(0))
    subject.read must beEqualTo(-1)
  }

  "read multiple bytes" in new BaseScope {
    val subject = lo("foo".getBytes("ascii"))
    val buffer = new Array[Byte](100)
    subject.read(buffer, 0, 2) must beEqualTo(2) // plus side-effect
    buffer(0) must beEqualTo("f".charAt(0))
    buffer(1) must beEqualTo("o".charAt(0))
    subject.read(buffer, 5, 2) must beEqualTo(1) // plus side-effect
    buffer(5) must beEqualTo("o".charAt(0))
    subject.read(buffer, 0, 1) must beEqualTo(-1)
  }

  "throw IOException if the object disappears" in new BaseScope {
    val subject = lo("some contents".getBytes("ascii"))
    val buffer = new Array[Byte](100)
    loApi.delete(subject.oid)
    connection.commit()
    subject.read must throwA[java.io.IOException]
    subject.read(buffer, 0, 1) must throwA[java.io.IOException]
  }
}
