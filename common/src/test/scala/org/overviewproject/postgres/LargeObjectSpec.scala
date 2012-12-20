package org.overviewproject.postgres

import org.overviewproject.test.DbSpecification
import org.overviewproject.database.DB
import org.postgresql.PGConnection
import org.postgresql.util.PSQLException


class LargeObjectSpec extends DbSpecification {

  step(setupDb)

  "LargeObject" should {

    trait LoContext extends DbTestContext {
      var oid: Long = _
      implicit var pgConnection: PGConnection = _

      override def setupWithDb = {
        pgConnection = DB.pgConnection
        oid = LO.withLargeObject { _.oid }
      }

      def addData(data: Array[Byte]): Long = {
        LO.withLargeObject(oid) { _.add(data) }
      }
    }

    "create a new instance" in new LoContext {
      oid must not be equalTo(-1) 
    }


    "throw exception for non existent oid" in new LoContext {
      LO.withLargeObject(234) { _.oid } must throwA[PSQLException]
    }

    "return a LargeObject if oid exists" in new LoContext {
      LO.withLargeObject(oid) { _.oid must be equalTo (oid) }
    }

    "add data" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)

      LO.withLargeObject(oid) { largeObject =>
        val size = largeObject.add(data)
        size must be equalTo (data.size)
      }

      LO.withLargeObject(oid) { largeObject =>
        val readData = new Array[Byte](4)
        largeObject.inputStream.read(readData)
        readData must be equalTo (data)
      }
    }

    "append data" in new LoContext {
      val allData = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)
      addData(allData.take(4)) must be equalTo (4)
      addData(allData.drop(4)) must be equalTo (8)

      LO.withLargeObject(oid) { largeObject =>
    	val readData = new Array[Byte](8)
        largeObject.inputStream.read(readData)
        readData must be equalTo (allData) 
      }
    }

    "truncate the stored data" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)
      addData(data) must be equalTo (4)

      LO.withLargeObject(oid) { largeObject =>
        largeObject.truncate
      }

      LO.withLargeObject(oid) { largeObject =>
        addData(data) must be equalTo (4)
      }
    }

    "delete the large object" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)
      addData(data)

      LO.delete(oid)

      LO.withLargeObject(oid) { largeObject =>
        largeObject.inputStream.read
      } must throwA[PSQLException]
    }

    "throw exception when deleting nonexisting objects" in new LoContext {
      LO.delete(223) must throwA[PSQLException]
    }

  }

  step(shutdownDb)

}
