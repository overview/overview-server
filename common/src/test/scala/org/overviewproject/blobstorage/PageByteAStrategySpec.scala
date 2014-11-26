package org.overviewproject.blobstorage

import org.overviewproject.models.tables.Files
import org.overviewproject.models.File
import org.overviewproject.models.tables.Pages
import org.overviewproject.models.Page
import org.overviewproject.test.SlickSpecification
import org.overviewproject.database.Slick.simple._
import scala.concurrent.Future

class PageByteAStrategySpec extends SlickSpecification with StrategySpecHelper {

  "PageByteAStrategy" should {

    "#get" should {

      "return an enumerator from data" in new ExistingPageScope {
        val future = strategy.get(s"pagebytea:${page.id}")
        val enumerator = await(future)
        val bytesRead = consume(enumerator)

        bytesRead must be equalTo data
      }

      "return an empty enumerator if data is not set" in new NoDataPageScope {
        val future = strategy.get(s"pagebytea:${page.id}")
        val enumerator = await(future)
        val bytesRead = consume(enumerator)

        bytesRead must beEmpty
      }
      
      "throw an exception when get location does not look like pagebytea:PAGEID" in new ExistingFileScope {
        invalidLocationThrowsException(strategy.get)
      }

      "throw a delayed exception if pageId is not a valid id" in new ExistingFileScope {
    	val future = strategy.get(s"pagebytea:0")
    	await(future) must throwA[Exception]
      }
    }

    "#delete" should {
      "not do anything" in {
        todo
      }
    }

    "#create" should {
      "throw UnimplementedException" in {
        todo
      }
    }
  }

  object DbFactory {

    private val insertFileInvoker = {
      val q = for (f <- Files) yield (f.referenceCount, f.contentsOid, f.viewOid, f.name, f.contentsSize, f.viewSize)
      (q returning Files).insertInvoker
    }

    private val insertPageInvoker = {
      val q = for (p <- Pages) yield (p.fileId, p.pageNumber, p.referenceCount, p.data)
      (q returning Pages).insertInvoker
    }

    private val insertPageNoDataInvoker = {
      val q = for (p <- Pages) yield (p.fileId, p.pageNumber, p.referenceCount)
      (q returning Pages).insertInvoker
    }

    def insertFile(implicit session: Session): File =
      insertFileInvoker.insert(1, 10l, 10l, "name", Some(100l), Some(100l))

    def insertPage(fileId: Long, data: Array[Byte])(implicit session: Session): Page =
      insertPageInvoker.insert(fileId, 1, 1, data)

    def insertPageNoData(fileId: Long)(implicit session: Session): Page =
      insertPageNoDataInvoker.insert(fileId, 1, 1)

  }

  trait PageBaseScope extends DbScope {

    class TestPageByteAStrategy(session: Session) extends PageByteAStrategy {
      import scala.concurrent.ExecutionContext.Implicits.global

      override def db[A](block: Session => A): Future[A] = Future {
        block(session)
      }
    }

    val strategy = new TestPageByteAStrategy(session)

    def invalidLocationThrowsException[T](f: String => T) =
      (f("pagebyteX:1234") must throwA[IllegalArgumentException]) and
        (f("pagebytea::1234") must throwA[IllegalArgumentException]) and
        (f("pagebytea:") must throwA[IllegalArgumentException])

  }

  trait ExistingFileScope extends PageBaseScope {
    val file = DbFactory.insertFile
  }

  trait ExistingPageScope extends ExistingFileScope {
    val data = Array[Byte](1, 2, 3)
    val page = DbFactory.insertPage(file.id, data)
  }

  trait NoDataPageScope extends ExistingFileScope {
    val page = DbFactory.insertPageNoData(file.id)
  }
}

