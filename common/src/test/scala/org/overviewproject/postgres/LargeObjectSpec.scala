package org.overviewproject.postgres

import org.postgresql.PGConnection
import org.postgresql.util.PSQLException

import org.overviewproject.test.DbSpecification
import org.overviewproject.database.DB

class LargeObjectSpec extends DbSpecification {
  "postgres.LargeObject (deprecated)" should {

    trait LoContext extends DbTestContext {
      var oid: Long = _
      implicit var pgConnection: PGConnection = _

      connection.setAutoCommit(false)

      override def setupWithDb = {
        pgConnection = DB.pgConnection
        oid = LO.withLargeObject { _.oid }
      }

      def addData(data: Array[Byte]): Long = {
        LO.withLargeObject(oid) { _.add(data) }
      }
      
      def insertData(data: Array[Byte], start: Int): Long = {
        LO.withLargeObject(oid) { _.insert(data, start) }
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

    "insert data" in new LoContext {
      val data1 = Array[Byte](1, 2, 3, 4)
      val data2 = Array[Byte](5, 6, 7, 8)
      val overwrittenData = data1.take(2) ++ data2
      
      insertData(data1, 0) must be equalTo 4
      insertData(data2, 2) must be equalTo 6
      
      LO.withLargeObject(oid) { largeObject =>
        val readData = new Array[Byte](6)
        largeObject.inputStream.read(readData)
        readData must be equalTo(overwrittenData)
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

    "read data into array" in new LoContext {
      val data = Array.tabulate[Byte](100)(b => b.toByte)
      addData(data)

      val readData = new Array[Byte](60)

      LO.withLargeObject(oid) { largeObject =>
        largeObject.read(readData, 0, 60) must be equalTo 60
        readData must be equalTo data.take(60)

        largeObject.read(readData, 10, 60) must be equalTo 40
        readData.drop(10).take(40) must be equalTo data.drop(60)

        largeObject.read(readData, 0, 100) must be equalTo 0
      }
    }

    "seek in the LargeObject" in new LoContext {
      val data = Array.tabulate[Byte](100)(b => b.toByte)
      addData(data)

      val readData = new Array[Byte](100)
      LO.withLargeObject(oid) { largeObject =>
        largeObject.read(readData, 0, 50)
      }

      LO.withLargeObject(oid) { largeObject =>
        largeObject.seek(50)
        largeObject.read(readData, 50, 50)
        readData must be equalTo(data)
      }
      
      LO.withLargeObject(oid) { largeObject =>
        largeObject.seek(500)
        largeObject.read(readData, 0, 100) must be equalTo 0
      }
    }
  }
}
