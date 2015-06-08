package org.overviewproject.database

import java.sql.SQLException
import slick.dbio.DBIO

import org.overviewproject.test.DbSpecification

class LargeObjectManagerSpec extends DbSpecification {
  "LargeObjectManager" should {
    trait BaseScope extends DbScope {
      val loManager = database.largeObjectManager
      val oid = run(loManager.create)

      override def after = {
        run(loManager.unlink(oid).asTry)
        super.after
      }

      def run[T](action: DBIO[T]): T = {
        import databaseApi._
        blockingDatabase.run(action.transactionally)
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope

      "return an OID" in new CreateScope {
        oid must not(beEqualTo(-1))
      }
    }

    "#open" should {
      trait OpenScope extends BaseScope

      "throw a programmer-readable error on invalid OID" in new OpenScope {
        run(loManager.open(oid + 1, LargeObject.Mode.Read)) must throwA[SQLException]
      }

      "return a LargeObject" in new OpenScope {
        run(loManager.open(oid, LargeObject.Mode.Read)).oid must beEqualTo(oid)
      }
    }

    "#unlink" should {
      trait UnlinkScope extends BaseScope

      "throw a programmer-readable error on invalid OID" in new UnlinkScope {
        run(loManager.unlink(oid + 1)) must throwA[SQLException]
      }

      "delete the LargeObject" in new UnlinkScope {
        run(loManager.unlink(oid))
        run(loManager.open(oid, LargeObject.Mode.Read)) must throwA[SQLException]
      }
    }
  }
}
