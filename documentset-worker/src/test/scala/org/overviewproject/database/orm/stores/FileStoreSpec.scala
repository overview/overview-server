package org.overviewproject.database.orm.stores

import org.overviewproject.database.orm.Schema.files
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.File

class FileStoreSpec extends DbSpecification {
  import org.overviewproject.postgres.SquerylEntrypoint._

  "FileStore" should {
    trait FileContext extends DbTestContext {
      protected def findByIds(ids: Iterable[Long]): Seq[File] = {
        from(files)(f =>
          where(f.id in ids)
            select (f)).toSeq
      }
    }

    "decrement the refcount when it is >1" in new FileContext {
      val multipleRefFile = files.insert(File(2, "name2", "loc2", 100L, "loc2", 100L))
      FileStore.removeReference(Seq(multipleRefFile.id))
      findByIds(Seq(multipleRefFile.id)).map(_.referenceCount) must beEqualTo(Seq(1))
    }

    "decrement the refcount when it is 1" in new FileContext {
      val singleRefFile = files.insert(File(1, "name1", "loc1", 100L, "loc1", 100L))
      FileStore.removeReference(Seq(singleRefFile.id))
      findByIds(Seq(singleRefFile.id)).map(_.referenceCount) must beEqualTo(Seq(0))
    }
  }
}
