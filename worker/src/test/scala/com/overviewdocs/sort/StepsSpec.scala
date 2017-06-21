package com.overviewdocs.sort

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink,Source}
import java.nio.file.{Files,Path}
import org.specs2.mutable.Specification
import org.specs2.specification.{After,Scope}
import scala.collection.{immutable,mutable}
import scala.concurrent.Future

import com.overviewdocs.util.AwaitMethod
import com.overviewdocs.test.ActorSystemContext

class StepsSpec extends Specification with AwaitMethod {
  sequential

  trait BaseScope extends Scope with ActorSystemContext with After {
    implicit val ec = system.dispatcher
    implicit val mat = ActorMaterializer.create(system)
    val tempDir: Path = Files.createTempDirectory("PageOnDiskSpec")

    // Relying on PageOnDisk makes this a bit more like an integration test
    // than a unit test. Oh well.
    def buildPage(records: immutable.Seq[Record]): PageOnDisk = {
      await(PageOnDisk.sortAndCreate(tempDir, records))
    }

    override def after = {
      import scala.compat.java8.FunctionConverters._

      Files.list(tempDir).forEach((Files.delete _).asJava)
      Files.deleteIfExists(tempDir)
    }

    def waitLeftThenTakeRight[A](left: Future[Unit], right: Future[A]): Future[A] = {
      for {
        _ <- left
        v <- right
      } yield v
    }

    def readAllRecordsAndWaitForFileDelete(records: Source[Record, Future[Unit]]): immutable.Seq[Record] = {
      await(records.toMat(Sink.seq)(waitLeftThenTakeRight).run)
    }
  }

  "Steps" should {
    "mergeAllPagesAtOnce" should {
      "merge a single page" in new BaseScope {
        val records = immutable.Seq(Record(0, 0, Array.empty), Record(1, 1, Array.empty))
        val page = buildPage(records)
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(page), _ => (), 1)
        readAllRecordsAndWaitForFileDelete(source.records) must beEqualTo(records)
      }

      "merge two pages" in new BaseScope {
        val records1 = immutable.Seq(
          Record(0, 0, Array.empty),
          Record(3, 3, Array.empty)
        )
        val records2 = immutable.Seq(
          Record(1, 1, Array.empty),
          Record(2, 2, Array.empty)
        )
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(
          buildPage(records1),
          buildPage(records2)
        ), _ => (), 1)
        source.nRecords must beEqualTo(4)
        readAllRecordsAndWaitForFileDelete(source.records) must beEqualTo((records1 ++ records2).sorted)
      }

      "merge three pages" in new BaseScope {
        val records1 = immutable.Seq(
          Record(0, 0, Array.empty),
          Record(4, 4, Array.empty)
        )
        val records2 = immutable.Seq(
          Record(1, 1, Array.empty),
          Record(2, 2, Array.empty)
        )
        val records3 = immutable.Seq(
          Record(3, 3, Array.empty),
          Record(5, 5, Array.empty)
        )
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(
          buildPage(records1),
          buildPage(records2),
          buildPage(records3)
        ), _ => (), 1)
        source.nRecords must beEqualTo(6)
        readAllRecordsAndWaitForFileDelete(source.records) must beEqualTo((records1 ++ records2 ++ records3).sorted)
      }

      "delete files" in new BaseScope {
        val page1 = buildPage(immutable.Seq(
          Record(0, 0, Array.empty),
          Record(4, 4, Array.empty)
        ))
        val page2 = buildPage(immutable.Seq(
          Record(1, 1, Array.empty),
          Record(2, 2, Array.empty)
        ))
        val page3 = buildPage(immutable.Seq(
          Record(3, 3, Array.empty),
          Record(5, 5, Array.empty)
        ))
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(page1, page2, page3), _ => (), 1)

        await(Sink.ignore.runWith(source.records))
        Files.exists(page1.path) must beEqualTo(false)
        Files.exists(page2.path) must beEqualTo(false)
        Files.exists(page3.path) must beEqualTo(false)
      }

      "call onProgress" in new BaseScope {
        val reports = mutable.ArrayBuffer.empty[Int]
        def onProgress(i: Int): Unit = reports.+=(i)

        val records1 = immutable.Seq(
          Record(0, 0, Array.empty),
          Record(3, 3, Array.empty),
          Record(5, 5, Array.empty)
        )
        val records2 = immutable.Seq(
          Record(1, 1, Array.empty),
          Record(2, 2, Array.empty)
        )
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(
          buildPage(records1),
          buildPage(records2)
        ), onProgress, 2)

        await(Sink.ignore.runWith(source.records))
        reports must beEqualTo(Seq(2, 4, 5))
      }
    }

    "calculateNMerges" should {
      "count a single source once" in new Scope {
        Steps.calculateNMerges(immutable.Seq(10), 2) must beEqualTo(10)
      }

      "calculate a single _merge_ once" in new Scope {
        Steps.calculateNMerges(immutable.Seq(10, 11, 12), 3) must beEqualTo(33)
      }

      "not crash if mergeFactor is larger than number of pages" in new Scope {
        Steps.calculateNMerges(immutable.Seq(10, 11), 3) must beEqualTo(21)
      }

      "calculate two merges twice" in new Scope {
        Steps.calculateNMerges(immutable.Seq(1, 2, 3, 4), 2) must beEqualTo(20)
      }

      "not count a merge for dangling pages" in new Scope {
        // The "4" in this example will only be processed once. 1+2+3 will be
        // processed twice.
        Steps.calculateNMerges(immutable.Seq(1, 2, 3, 4), 3) must beEqualTo(16)
      }

      "count less-than-mergeFactor sized merges when they are needed" in new Scope {
        // 1. Merge (1,2,3), (4,5,6), and (7,8)
        // 2. Merge (123,456,78)
        // (This isn't optimal, but it's simple)
        Steps.calculateNMerges(immutable.Seq(1, 2, 3, 4, 5, 6, 7, 8), 3) must beEqualTo(
          1+2+3 + 4+5+6 + 7+8 +
          1+2+3+4+5+6+7+8
        )
      }

      "skip less-than-mergeFactor sized merges when they are not needed" in new Scope {
        // 1. Merge (1,2,3,4) and (5,6,7,8)
        // 2. Merge (1234,5678,9,10)
        Steps.calculateNMerges(immutable.Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4) must beEqualTo(
          1+2+3+4 + 5+6+7+8 +
          1+2+3+4+5+6+7+8+9+10
        )
      }

      "return 0 when there are no pages" in new Scope {
        Steps.calculateNMerges(immutable.Seq(), 2) must beEqualTo(0)
      }
    }

    "mergePagesUntilMRemain" should {
      trait BaseScopeWith5Pages extends BaseScope {
        val records1 = immutable.Seq(
          Record(0, 0, Array.empty),
          Record(4, 4, Array.empty)
        )
        val records2 = immutable.Seq(
          Record(1, 1, Array.empty),
          Record(2, 2, Array.empty)
        )
        val records3 = immutable.Seq(
          Record(3, 3, Array.empty),
          Record(9, 9, Array.empty)
        )
        val records4 = immutable.Seq(
          Record(5, 5, Array.empty),
          Record(8, 8, Array.empty)
        )
        val records5 = immutable.Seq(
          Record(6, 6, Array.empty),
          Record(7, 7, Array.empty)
        )
        val pages = immutable.Seq(records1, records2, records3, records4, records5).map(buildPage _)
      }

      "be a no-op if nPages < M" in new BaseScopeWith5Pages {
        var onProgressCalled = false
        def onProgress(i: Int): Unit = { onProgressCalled = true }

        val ret = await(Steps.mergePagesUntilMRemain(
          pages,
          tempDir,
          5,
          onProgress,
          1
        ))
        ret must beEqualTo(pages)
        onProgressCalled must beEqualTo(false)
      }

      "call mergeAllPagesAtOnce on sub-lists" in new BaseScopeWith5Pages {
        val progressReports = mutable.ArrayBuffer.empty[Int]
        def onProgress(i: Int): Unit = { progressReports.append(i) }

        val ret = await(Steps.mergePagesUntilMRemain(pages, tempDir, 4, onProgress, 2))

        ret.size must beEqualTo(2)
        ret(0).nRecords must beEqualTo(8)
        readAllRecordsAndWaitForFileDelete(ret(0).toSourceDestructive).map(_.id) must beEqualTo(
          Seq(0, 1, 2, 3, 4, 5, 8, 9)
        )
        progressReports must beEqualTo(Seq(2, 4, 6, 8))
      }

      "merge with more than one pass when needed" in new BaseScopeWith5Pages {
        val progressReports = mutable.ArrayBuffer.empty[Int]
        def onProgress(i: Int): Unit = { progressReports.append(i) }

        val ret = await(Steps.mergePagesUntilMRemain(pages, tempDir, 2, onProgress, 2))

        ret.size must beEqualTo(2)
        ret(0).nRecords must beEqualTo(8)
        readAllRecordsAndWaitForFileDelete(ret(0).toSourceDestructive).map(_.id) must beEqualTo(
          Seq(0, 1, 2, 3, 4, 5, 8, 9)
        )
        progressReports must beEqualTo(Seq(2, 4, 6, 8, 10, 12, 14, 16))
      }
    }

    "recordSourceToIdArray" should {
      "create an empty Array" in new BaseScope {
        val recordSource = RecordSource(
          4,
          Source(immutable.Seq(
            Record(2, 0, Array.empty),
            Record(1, 0, Array.empty),
            Record(3, 0, Array.empty),
            Record(0, 0, Array.empty)
          )).mapMaterializedValue(_ => Future.successful(()))
        )
        val ret = await(Steps.recordSourceToIdArray(recordSource))
        ret.toSeq must beEqualTo(Seq(2, 1, 3, 0))
      }
    }
  }
}
