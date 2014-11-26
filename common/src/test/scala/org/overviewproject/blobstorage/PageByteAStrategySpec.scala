package org.overviewproject.blobstorage

import org.overviewproject.models.tables.Files
import org.overviewproject.models.File
import org.overviewproject.models.tables.Pages
import org.overviewproject.models.Page
import org.overviewproject.test.SlickSpecification

class PageByteAStrategySpec extends SlickSpecification {

  "PageByteAStrategy" should {

    "#get" should {

      "return an enumerator from data" in new ExistingPageScope {
        //val future = TestPageByteAStrategy.get(s"pagebytea:${page.id}")
        //val enumerator = await(future)
        //val bytesRead = Array[Byte](4,5,6) //consume(enumerator)
        
       todo
      }
    }

    "#delete" should {

    }

    "#create" should {

    }
  }

  object DbFactory {
    import org.overviewproject.database.Slick.simple._

    private val insertFileInvoker = (Files returning Files).insertInvoker
    private val insertPageInvoker = (Pages returning Pages).insertInvoker

    def insertFile(implicit session: Session): File = {
      val file = File(0l, 1, 10l, 10l, "name", Some(100l), Some(100l))
      insertFileInvoker.insert(file)
    }
    
    def insertPage(fileId: Long, data: Array[Byte])(implicit session: Session): Page = {
      val page = Page(0l, fileId, 1, 1, Some(data), None)
      insertPageInvoker.insert(page)
    }
  }
  
  trait PageBaseScope extends DbScope {
    object TestPageByteAStrategy extends PageByteAStrategy
  }
  
  trait ExistingFileScope extends PageBaseScope {
    val file = DbFactory.insertFile
  }
  
  trait ExistingPageScope extends ExistingFileScope {
    val data = Array[Byte](1, 2, 3)
    val page = DbFactory.insertPage(file.id, data)
  }
}

