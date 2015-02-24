package org.overviewproject.blobstorage

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.ExecutionContext
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.models.{File,Page}
import org.overviewproject.models.tables.{Files,Pages}
import org.overviewproject.test.SlickSpecification

class PageByteAStrategySpec extends SlickSpecification with StrategySpecHelper {
  trait BaseScope extends DbScope {
    class TestPageByteAStrategy(session: Session) extends PageByteAStrategy {
      override def db[A](block: Session => A)(implicit executor: ExecutionContext) = Future(block(session))
    }

    val strategy = new TestPageByteAStrategy(session)
  }

  "#get" should {
    trait GetScope extends BaseScope {
      val file = Db.insertFile
    }

    "return an enumerator from data" in new GetScope {
      val page = Db.insertPage(file.id, Some("blah".getBytes("utf-8")), 4L)
      val enumerator = await(strategy.get(s"pagebytea:${page.id}"))
      consume(enumerator) must beEqualTo("blah".getBytes("utf-8"))
    }

    "throw a delayed exception if data is NULL" in new GetScope {
      val page = Db.insertPage(file.id, None, 4L)
      val future = strategy.get(s"pagebytea:${page.id}")
      await(future) must throwA[Exception]
    }

    "throw an exception when get location does not look like pagebytea:PAGEID" in new GetScope {
      await(strategy.get("pagebyteX:1234")) must throwA[IllegalArgumentException]
      await(strategy.get("pagebytea::1234")) must throwA[IllegalArgumentException]
      await(strategy.get("pagebytea:")) must throwA[IllegalArgumentException]
    }

    "throw a delayed exception if pageId is not a valid id" in new GetScope {
      val page = Db.insertPage(file.id, Some("blah".getBytes("utf-8")), 4L)
      await(strategy.get(s"pagebytea:${page.id + 1L}")) must throwA[Exception]
    }
  }

  "#delete" should {
    "not do anything" in new BaseScope {
      val file = Db.insertFile
      val page = Db.insertPage(file.id, Some("blah".getBytes("utf-8")), 4L)
      await(strategy.delete(s"pagebytea:${page.id}"))
      Db.getPage(page.id) must beSome
    }
  }

  "#create" should {
    "throw NotImplementedError" in new BaseScope { 
      val contentStream = new ByteArrayInputStream("blah".getBytes("utf-8"))
      strategy.create("pagebytea", contentStream, 4) must throwA[NotImplementedError]
    }
  }

  object Db {
    import org.overviewproject.database.Slick.simple.{Session=>XXX,_}

    private val insertFileInvoker = {
      val q = for (f <- Files) yield (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.viewLocation, f.viewSize)
      (q returning Files).insertInvoker
    }

    private val insertPageInvoker = {
      val q = for (p <- Pages) yield (p.fileId, p.pageNumber, p.data, p.dataSize)
      (q returning Pages).insertInvoker
    }

    def insertFile(implicit session: Session): File = {
      insertFileInvoker.insert(1, "name", "location", 10L, "location", 10L)(session)
    }

    def insertPage(fileId: Long, data: Option[Array[Byte]], length: Long)(implicit session: Session): Page = {
      insertPageInvoker.insert(fileId, 1, data, length)(session)
    }

    def getPage(id: Long)(implicit session: Session): Option[Page] = {
      Pages.filter(_.id === id).firstOption(session)
    }
  }
}
