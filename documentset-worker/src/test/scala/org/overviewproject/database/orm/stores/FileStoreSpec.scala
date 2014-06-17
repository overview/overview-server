package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.database.DB
import org.overviewproject.postgres.LO
import org.overviewproject.tree.orm.File
import org.overviewproject.database.orm.Schema.files
import org.squeryl.Query
import scala.util.Try

class FileStoreSpec extends DbSpecification {

  step(setupDb)

  "FileStore" should {

    trait FileContext extends DbTestContext {

      var singleRefOids: Seq[Long] = _
      var multipleRefOids: Seq[Long] = _

      override def setupWithDb {
        singleRefOids = Seq.fill(5)(createContents)
        multipleRefOids = Seq.fill(5)(createContents)

        val singleRefFiles = singleRefOids.map(oid => File(1, oid, oid,  "name1"))
        val multipleRefFiles = multipleRefOids.map(oid => File(2, oid, oid, "name2"))
        files.insert(singleRefFiles ++ multipleRefFiles)
      }

      private def createContents: Long = {
        implicit val pgConnection = DB.pgConnection

        LO.withLargeObject(_.oid)
      }

      protected def findSingleRefFileIds: Seq[Long] =
        from(files)(f =>
          where(f.referenceCount === 1)
            select (f.id)).toSeq

      protected def findMultipleRefFileIds: Seq[Long] =
        from(files)(f =>
          where(f.referenceCount gt 1)
            select (f.id)).toSeq

      protected def findZeroRefFileIds: Seq[Long] =
        from(files)(f =>
          where(f.referenceCount === 0)
            select (f.id)).toSeq

      protected def findByIds(ids: Iterable[Long]): Seq[File] =
        from(files)(f =>
          where(f.id in ids)
            select (f)).toSeq

      // This test generates an exception which aborts the transaction
      // No further database access is possible, so make this the last 
      // assertion in the test
      protected def contentIsRemoved(oid: Long): Boolean = {
        implicit val pgConnection = DB.pgConnection

        val findOid = Try(LO.withLargeObject(oid)(lo => lo.oid))
        findOid.isFailure
      }
    }

    "keep file if refcount > 0 after reference removal" in new FileContext {
      val fileIds = findMultipleRefFileIds

      FileStore.removeReference(fileIds)

      val updatedFiles = findByIds(fileIds)

      updatedFiles.map(_.referenceCount must be equalTo (1))
      multipleRefOids.map(oid => contentIsRemoved(oid) must beFalse)
    }

    "delete file if refcount == 0 after reference removal" in new FileContext {
      val fileIds = findSingleRefFileIds

      FileStore.removeReference(fileIds)

      val updatedFiles = findByIds(fileIds)

      updatedFiles must beEmpty
      findZeroRefFileIds must beEmpty

      singleRefOids.map(oid => contentIsRemoved(oid) must beTrue)
    }
  }

  step(shutdownDb)
}