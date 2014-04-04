package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.database.orm.Schema.{ files, pages }
import org.overviewproject.tree.orm.{ File, Page }

class PageStoreSpec extends DbSpecification {

  step(setupDb)

  trait PageContext extends DbTestContext {
	protected val numberOfPages = 10
	
    override def setupWithDb = {
      val file = files.insertOrUpdate(File(1, 1l, "name"))
      val pageData: Array[Byte] = Array.fill(128)(0xCC.toByte)

      val pagesToInsert = Seq.fill(numberOfPages)(Page(file.id, 1, pageData, refCount))
      pages.insert(pagesToInsert)
    }

    protected def refCount = 1

    protected def findPages: Seq[Page] =
      from(pages)(p =>
        select(p)).toSeq
  }

  "delete page if refcount is 0 after reference removal" in new PageContext {
    val pageIds = findPages.map(_.id)
    PageStore.removeReference(pageIds)

    findPages must beEmpty
  }

  "keep page if refcount is >0 after reference removal" in new PageContext {
    override protected def refCount = 2

    val pageIds = findPages.map(_.id)
    PageStore.removeReference(pageIds)

    val pageReferenceCounts = findPages.map(_.referenceCount)
    
    pageReferenceCounts must have size(numberOfPages)
    pageReferenceCounts must contain(1).forall
  }

  step(shutdownDb)
}