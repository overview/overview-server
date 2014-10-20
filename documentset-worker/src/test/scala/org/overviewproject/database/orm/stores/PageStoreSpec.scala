package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.database.orm.Schema.{ files, pages }
import org.overviewproject.tree.orm.{ File, Page }

class PageStoreSpec extends DbSpecification {

  step(setupDb)

  trait PageContext extends DbTestContext {
    protected val numberOfFiles = 3
	protected val numberOfPagesInFile = 10
	protected val totalNumberOfPages = numberOfFiles * numberOfPagesInFile
	
	protected var fileIds: Seq[Long] = _
	
    override def setupWithDb = {
      val filesWithPages = Seq.tabulate(numberOfFiles)(n => File(1, n, n, s"name-$n", Some(100), Some(100)))
      files.insert(filesWithPages)
      
      fileIds = findFileIds
      
      val pageData: Array[Byte] = Array.fill(128)(0xCC.toByte)

      fileIds.foreach { fileId =>
        val pagesToInsert = Seq.fill(numberOfPagesInFile)(Page(fileId, 1, refCount, Some(pageData), None))
        pages.insert(pagesToInsert)        
      }
      
      
    }

    protected def refCount = 1

    protected def findFileIds: Seq[Long] =
      from(files)(f => select (f.id)).toSeq
      
    protected def findPages: Seq[Page] =
      from(pages)(p =>
        select(p)).toSeq
  }

  "delete page if refcount is 0 after reference removal" in new PageContext {
    PageStore.removeReferenceByFile(fileIds)

    findPages must beEmpty
  }

  "keep page if refcount is >0 after reference removal" in new PageContext {
    override protected def refCount = 2

    PageStore.removeReferenceByFile(fileIds) 

    val pageReferenceCounts = findPages.map(_.referenceCount)
    
    pageReferenceCounts must have size(totalNumberOfPages)
    pageReferenceCounts must contain(1).forall
  }

  step(shutdownDb)
}