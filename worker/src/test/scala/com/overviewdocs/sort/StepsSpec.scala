package com.overviewdocs.sort

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
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
  }

  "Steps" should {
    "mergeAllPagesAtOnce" should {
      "merge a single page" in new BaseScope {
        val records = immutable.Seq(Record(0, 0, Array.empty), Record(1, 1, Array.empty))
        val page = buildPage(records)
        val source = Steps.mergeAllPagesAtOnce(immutable.Seq(page), _ => (), 1)
        await(source.records.toMat(Sink.seq)(waitLeftThenTakeRight).run) must beEqualTo(records)
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
        await(source.records.toMat(Sink.seq)(waitLeftThenTakeRight).run) must beEqualTo((records1 ++ records2).sorted)
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
        await(source.records.toMat(Sink.seq)(waitLeftThenTakeRight).run) must beEqualTo((records1 ++ records2 ++ records3).sorted)
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
  }
}
