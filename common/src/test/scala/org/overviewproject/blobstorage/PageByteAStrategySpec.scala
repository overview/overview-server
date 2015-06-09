package org.overviewproject.blobstorage

import java.io.ByteArrayInputStream
import scala.concurrent.Future

import org.overviewproject.database.BlockingDatabaseProvider
import org.overviewproject.models.{File,Page}
import org.overviewproject.models.tables.{Files,Pages}
import org.overviewproject.test.DbSpecification

class PageByteAStrategySpec extends DbSpecification with StrategySpecHelper {
  trait BaseScope extends DbScope {
    val strategy = PageByteAStrategy
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

  object Db extends BlockingDatabaseProvider {
    import blockingDatabaseApi._

    private val fileInserter = {
      val q = for (f <- Files) yield (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.viewLocation, f.viewSize)
      (q returning Files)
    }

    private val pageInserter = {
      val q = for (p <- Pages) yield (p.fileId, p.pageNumber, p.data, p.dataSize)
      (q returning Pages)
    }

    def insertFile: File = {
      blockingDatabase.run(fileInserter.+=(1, "name", "location", 10L, "location", 10L))
    }

    def insertPage(fileId: Long, data: Option[Array[Byte]], length: Long): Page = {
      blockingDatabase.run(pageInserter.+=(fileId, 1, data, length))
    }

    def getPage(id: Long): Option[Page] = {
      blockingDatabase.option(Pages.filter(_.id === id))
    }
  }
}
