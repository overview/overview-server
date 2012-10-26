package models.upload

import com.jolbox.bonecp.ConnectionHandle
import org.postgresql.PGConnection
import org.postgresql.util.PSQLException
import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.specs2.mutable.Specification
import play.api.db.DB
import play.api.test.FakeApplication
import play.api.Play.{ current, start, stop }

class LargeObjectSpec extends Specification {

  step(start(FakeApplication()))

  "LargeObject" should {

    trait PGConnectionContext extends Around {
      implicit var pgConnection: PGConnection = _

      def setupWithDb = {}
      
      def around[T <% Result](test: => T) = {
        val connection = DB.getConnection(autocommit = false)
        try {
          val connectionHandle = connection.asInstanceOf[ConnectionHandle]
          pgConnection = connectionHandle.getInternalConnection.asInstanceOf[PGConnection]

	  setupWithDb
	  
          test
        } finally {
          connection.rollback()
          connection.close()
        }
      }
    }

    trait LoContext extends PGConnectionContext {
      var oid: Long = _
      
      override def setupWithDb = {
	LO.withLargeObject { largeObject =>
	  oid = largeObject.oid
	}
      }

      def addData(data: Array[Byte]): Long = {
	LO.withLargeObject(oid) { largeObject =>
	  largeObject.add(data)
	}.get
      }


    }

    "create a new instance" in new PGConnectionContext {
      val oid = LO.withLargeObject { largeObject =>
	largeObject.oid 
      }

      oid must not be equalTo(-1)
    }

    // See if there are better ways to handle errors.
    "return None for non existent oid" in new PGConnectionContext {
      val noid = LO.withLargeObject(234) { largeObject =>
	largeObject.oid
      }
      noid must beNone
    }

    "return a LargeObject if oid exists" in new LoContext {
      LO.withLargeObject(oid) { largeObject =>
        largeObject.oid must be equalTo (oid)
      }
    }

    "add data" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)
      
      LO.withLargeObject(oid) { largeObject =>
	val size = largeObject.add(data)
	size must be equalTo(data.size)
      }


      LO.withLargeObject(oid) { largeObject =>
	val readData = largeObject.read(10)
	readData must be equalTo(data)
      }
    }

    "append data" in new LoContext {
      val allData = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)
      addData(allData.take(4)) must be equalTo(4)
      addData(allData.drop(4)) must be equalTo(8)

      LO.withLargeObject(oid) { largeObject =>
	val readData = largeObject.read(10)
	readData must be equalTo(allData)
      }
    }

    "truncate the stored data" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)
      addData(data) must be equalTo(4)

      LO.withLargeObject(oid) { largeObject =>
	largeObject.truncate
	addData(data) must be equalTo(4)
      }
    }

    "delete the large object" in new LoContext {
      val data = Array[Byte](1, 2, 3, 4)
      addData(data)

      LO.delete(oid)

      LO.withLargeObject(oid) { largeObject =>
	largeObject.read(10)
      } must beNone
    }

    "throw exception when deleting nonexisting objects" in new PGConnectionContext {
      LO.delete(223) must throwA[PSQLException]
    }
  }

  step(stop)

}
