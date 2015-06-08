package org.overviewproject.database

import slick.dbio.DBIO

import org.overviewproject.test.DbSpecification

class LargeObjectSpec extends DbSpecification {
  "LargeObject" should {
    trait BaseScope extends DbScope {
      val loManager = database.largeObjectManager
      val oid = run(loManager.create)

      override def after = {
        run(loManager.unlink(oid))
        super.after
      }

      def run[T](action: DBIO[T]): T = {
        import databaseApi._
        blockingDatabase.run(action.transactionally)
      }
    }

    // We test #seek implicitly, by testing #read and #write

    "#read" should {
      trait ReadScope extends BaseScope

      "read an empty Array" in new ReadScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.Read)
          bytes <- lo.read(1024)
        } yield bytes) must beEqualTo(Array[Byte]())
      }

      "read part of the data" in new ReadScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.ReadWrite)
          _ <- lo.write("1234567890".getBytes("ascii"))
          _ <- lo.seek(0)
          bytes <- lo.read(5)
        } yield bytes) must beEqualTo("12345".getBytes("ascii"))
      }

      "return an Array shorter than requested when at end of file" in new ReadScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.ReadWrite)
          _ <- lo.write("12345".getBytes("ascii"))
          _ <- lo.seek(0)
          bytes <- lo.read(10)
        } yield bytes) must beEqualTo("12345".getBytes("ascii"))
      }
    }

    "#write" should {
      trait WriteScope extends BaseScope

      "write the entire buffer" in new WriteScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.ReadWrite)
          _ <- lo.write("12345".getBytes("ascii"))
          _ <- lo.seek(0)
          bytes <- lo.read(5)
        } yield bytes) must beEqualTo("12345".getBytes("ascii"))
      }

      "write the specified part of the buffer" in new WriteScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.ReadWrite)
          _ <- lo.write("1234567".getBytes("ascii"), 1, 5)
          _ <- lo.seek(0)
          bytes <- lo.read(6) // go past end of file to check "7" wasn't written
        } yield bytes) must beEqualTo("23456".getBytes("ascii"))
      }

      "write starting midway through the file" in new WriteScope {
        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.Write)
          _ <- lo.write("12345".getBytes("ascii"))
        } yield ())

        run(for {
          lo <- loManager.open(oid, LargeObject.Mode.ReadWrite)
          _ <- lo.seek(3)
          _ <- lo.write("12345".getBytes("ascii"))
          _ <- lo.seek(0)
          bytes <- lo.read(10)
        } yield bytes) must beEqualTo("12312345".getBytes("ascii"))
      }
    }
  }
}
