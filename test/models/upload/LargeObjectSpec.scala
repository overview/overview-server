package models.upload

import helpers.PgConnectionContext
import org.postgresql.util.PSQLException
import org.specs2.mutable.{ Around, Specification }
import play.api.Play.{ current, start, stop }
import play.api.test.FakeApplication
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LargeObjectSpec extends Specification {

  step(start(FakeApplication()))

  "LargeObject" should {

    trait LoContext extends PgConnectionContext {
      var oid: Long = _

      override def setupWithDb = {
        oid = LO.withLargeObject { _.oid }
      }

      def addData(data: Array[Byte]): Long = {
        LO.withLargeObject(oid) { _.add(data) }
      }
    }

    "create a new instance" in new PgConnectionContext {
      LO.withLargeObject { _.oid must not be equalTo(-1) }
    }

    // See if there are better ways to handle errors.
    "throw exception for non existent oid" in new PgConnectionContext {
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
        val readData = largeObject.read(10)
        readData must be equalTo (data)
      }
    }

    "append data" in new LoContext {
      val allData = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)
      addData(allData.take(4)) must be equalTo (4)
      addData(allData.drop(4)) must be equalTo (8)

      LO.withLargeObject(oid) { _.read(10) must be equalTo (allData) }
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

      LO.withLargeObject(oid) { _.read(10) } must throwA[PSQLException]
    }

    "throw exception when deleting nonexisting objects" in new PgConnectionContext {
      LO.delete(223) must throwA[PSQLException]
    }
  }

  step(stop)

}
