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

    trait LoWith255 extends LoContext {
      override val data = Array[Byte](255.toByte)
    }
    
    "read one byte at a time from chunk" in new LoContext {
      val readData = Array.fill(BufferSize)(loInputStream.read.toByte)
       
      readData must be equalTo data.take(BufferSize)
    }
    
    "read beyond buffer size" in new LoContext {
      val readData = new Array[Byte](100)
      
      loInputStream.read(readData, 0, 100) must be equalTo 100
      
      readData must be equalTo data
    }
    
    "read beyond the end of the LargeObject" in new LoContext {
      val readData = new Array[Byte](300)
      
      loInputStream.read(readData, 0, 200) must be equalTo 100
      
      readData.take(100) must be equalTo data
      
      loInputStream.read(readData, 0, 100) must be equalTo -1
    }
    
    "close stream" in new LoContext {
      loInputStream.close()
      loInputStream.read must throwA[java.io.IOException]
    }
    
    "throw IOException on error" in new DbTestContext {
      val loInputStream = new LargeObjectInputStream(-1)
      loInputStream.read must throwA[java.io.IOException]
    }
    
    "not read 255 as end of stream" in new LoWith255 {
      val b = loInputStream.read
      b must be equalTo 0x00ff
    }
  }
  step(shutdownDb)
}