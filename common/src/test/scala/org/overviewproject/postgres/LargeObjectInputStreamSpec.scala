package org.overviewproject.postgres

import org.overviewproject.database.{LargeObject=>GoodLargeObject}
import org.overviewproject.test.DbSpecification

class LargeObjectInputStreamSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    import databaseApi._

    val loManager = blockingDatabase.largeObjectManager

    val oidsToDelete = scala.collection.mutable.Set.empty[Long]

    def lo(loData: Array[Byte]): LargeObjectInputStream = {
      // FIXME this large object will leak, as we don't clear large objects
      // on test-suite start
      val lois = blockingDatabase.run((for {
        oid <- loManager.create
        lo <- loManager.open(oid, GoodLargeObject.Mode.Write)
        _ <- lo.write(loData)
      } yield new LargeObjectInputStream(oid, blockingDatabase)).transactionally)
      oidsToDelete.add(lois.oid)
      lois
    }

    def unlink(oid: Long): Unit = {
      blockingDatabase.run(loManager.unlink(oid).transactionally)
      oidsToDelete.remove(oid)
    }

    override def after: Unit = {
      oidsToDelete.toSeq.foreach(unlink _)
      super.after
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
    unlink(subject.oid)
    subject.read must throwA[java.io.IOException]
    subject.read(buffer, 0, 1) must throwA[java.io.IOException]
  }
}
