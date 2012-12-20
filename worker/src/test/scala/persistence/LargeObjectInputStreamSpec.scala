package persistence

import org.overviewproject.test.DbSpecification
import org.postgresql.PGConnection
import org.overviewproject.database.DB
import org.overviewproject.postgres.LO

class LargeObjectInputStreamSpec extends DbSpecification {

  step(setupDb)

  "LargeObjectInputStream" should {

    trait LoContext extends DbTestContext {
      implicit var pgConnection: PGConnection = _
      val data = Array.tabulate[Byte](100)(i => i.toByte)
      val BufferSize: Int = 10
      
      var loInputStream: LargeObjectInputStream = _
      
      override def setupWithDb = {
        pgConnection = DB.pgConnection
        LO.withLargeObject { largeObject =>
          largeObject.add(data)
          loInputStream = new LargeObjectInputStream(largeObject.oid, BufferSize)
        }
      }
    }

    "read one byte at a time from chunk" in new LoContext {
      val readData = Array.fill(BufferSize)(loInputStream.read.toByte)
       
      readData must be equalTo data.take(BufferSize)
    }

  }
  step(shutdownDb)
}