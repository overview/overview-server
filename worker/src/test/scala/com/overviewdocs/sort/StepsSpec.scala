package com.overviewdocs.sort

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Sink,Source}
import java.nio.file.{Files,Path}
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import scala.collection.{immutable,mutable}
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.util.AwaitMethod
import com.overviewdocs.test.ActorSystemContext

class StepsSpec extends Specification with AwaitMethod {
  sequential

  private def consumeRecordSource(recordSource: RecordSource)(implicit executionContext: ExecutionContext, mat: Materializer): Unit = {
    // We need to wait for the RecordSource's materialized value (which tells us
    // cleanup has happened)_and_ the materialized value of Sink.ignore (which
    // tells us all elements have been read).
    val runnableGraph = recordSource.records.toMat(Sink.ignore) { case (cleanupFuture, sinkFuture) =>
      sinkFuture.flatMap(_ => cleanupFuture)
    }
    await(runnableGraph.run)
  }

  trait BaseScope extends Scope with ActorSystemContext with After {
    implicit val ec = system.dispatcher
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

        consumeRecordSource(source)
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

        consumeRecordSource(source)
        reports.toList must beEqualTo(List(2, 4, 5))
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

    "mergePages" should {
      "be consistent in progress reports" in new BaseScopeWith5Pages {
        val progress = mutable.ArrayBuffer.empty[(Int,Int)]
        def onProgress(nMerged: Int, nMerges: Int): Unit = { progress.append((nMerged, nMerges)) }

        val recordSource = Steps.mergePages(pages, tempDir, 2, onProgress, 2)
        consumeRecordSource(recordSource)

        progress.toList must beEqualTo(List(
          (2,28), (4,28), (6,28), (8,28), (10,28), (12,28), (14,28), (16,28), (20,28), (22,28), (24,28), (26,28), (28,28)
        ))
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
          )).mapMaterializedValue(_ => Future.unit)
        )
        val ret = await(Steps.recordSourceToIds(recordSource))
        ret must beEqualTo(Vector(2, 1, 3, 0))
      }
    }

    "recordsToPages" should {
      trait BaseScopeWith5Records extends BaseScope {
        // These tests are integration-test-y: we assume Record and PageOnDisk
        // behave the way we expect.

        val records = immutable.Seq(                                   // nBytesEstimate:
          Record(0, 5, Array(1.toByte, 2.toByte, 3.toByte)),           // 39 bytes
          Record(1, 1, Array(1.toByte, 2.toByte)),                     // 38 bytes
          Record(2, 2, Array(1.toByte, 2.toByte, 3.toByte, 4.toByte)), // 40 bytes
          Record(4, 4, Array(1.toByte, 2.toByte, 3.toByte, 4.toByte)), // 40 bytes
          Record(3, 3, Array.empty[Byte])                              // 36 bytes
        )
      }

      "write all to a Page" in new BaseScopeWith5Records {
        val result = await(Steps.recordsToPages(Source(records), tempDir, 1000000, (_, _) => (), 1))
        result.size must beEqualTo(1)
        result(0).nRecords must beEqualTo(5)
        readAllRecordsAndWaitForFileDelete(result(0).toSourceDestructive) must beEqualTo(records.sorted)
      }

      "report progress while streaming" in new BaseScopeWith5Records {
        val progress = mutable.ArrayBuffer.empty[(Int,Long)]
        def onProgress(n: Int, b: Long): Unit = { progress.append((n, b)) }
        await(Steps.recordsToPages(Source(records), tempDir, 1000000, onProgress, 2))
        progress must beEqualTo(Seq((2, 77), (4, 157))) //, (5, 193)))
        // [adam, 2017-06-22] It might be nice to progress-report the final
        // (5, 193) too, but I can't seem to form an opinion about that and I've
        // already coded the implementation without it.
      }

      "divide into many pages" in new BaseScopeWith5Records {
        val result = await(Steps.recordsToPages(Source(records), tempDir, 100, (_, _) => (), 1))
        result.size must beEqualTo(3)
        result.map(_.nRecords) must beEqualTo(Seq(2, 2, 1))
        readAllRecordsAndWaitForFileDelete(result(0).toSourceDestructive).map(_.id) must beEqualTo(Seq(1, 0))
        readAllRecordsAndWaitForFileDelete(result(1).toSourceDestructive).map(_.id) must beEqualTo(Seq(2, 4))
        readAllRecordsAndWaitForFileDelete(result(2).toSourceDestructive).map(_.id) must beEqualTo(Seq(3))
      }

      "make sure if a record exceeds maxNBytes on its own, it gets a page" in new BaseScopeWith5Records {
        val result = await(Steps.recordsToPages(Source(records), tempDir, 1, (_, _) => (), 1))
        result.size must beEqualTo(5)
        result.map(_.nRecords) must beEqualTo(Seq(1, 1, 1, 1, 1))
      }
    }
  }
}
