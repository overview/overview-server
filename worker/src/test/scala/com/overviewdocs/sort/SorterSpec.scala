package com.overviewdocs.sort

import akka.stream.scaladsl.Source
import java.nio.file.{Files,Path}
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import scala.collection.{immutable,mutable}
import scala.concurrent.Future

import com.overviewdocs.util.AwaitMethod
import com.overviewdocs.test.ActorSystemContext

class SorterSpec extends Specification with AwaitMethod {
  sequential

  trait BaseScope extends Scope with ActorSystemContext with After {
    implicit val ec = system.dispatcher

    val tempDir: Path = Files.createTempDirectory("PageOnDiskSpec")

    override def after = {
      import scala.compat.java8.FunctionConverters._

      Files.list(tempDir).forEach((Files.delete _).asJava)
      Files.deleteIfExists(tempDir)
    }

    val config = SortConfig(
      firstReadNRecordsPerProgressCall=2,
      mergeNRecordsPerProgressCall=1,
      tempDirectory=tempDir,
      maxNBytesInMemory=100,
      mergeFactor=4
    )

    lazy val sorter = new Sorter(config)
  }

  "Sorter" should {
    "sort IDs" in new BaseScope {
      val result = await(sorter.sortIds(
        RecordSource(5, Source(immutable.Seq(
          Record(1, 4, Array(1.toByte, 2.toByte)),
          Record(2, 3, Array(4.toByte, 2.toByte)),
          Record(3, 2, Array(1.toByte, 1.toByte, 1.toByte)),
          Record(0, 1, Array(1.toByte, 2.toByte)),
          Record(4, 0, Array(3.toByte, 7.toByte))
        )).mapMaterializedValue(_ => Future.unit)),
        _ => ()
      ))
      result must beEqualTo(Vector(3, 0, 1, 4, 2))
    }

    "delete all temporary files during sort" in new BaseScope {
      await(sorter.sortIds(
        RecordSource(5, Source(immutable.Seq(
          Record(1, 4, Array(1.toByte, 2.toByte)),
          Record(2, 3, Array(4.toByte, 2.toByte)),
          Record(3, 2, Array(1.toByte, 1.toByte, 1.toByte)),
          Record(0, 1, Array(1.toByte, 2.toByte)),
          Record(4, 0, Array(3.toByte, 7.toByte))
        )).mapMaterializedValue(_ => Future.unit)),
        _ => ()
      ))
      Files.list(tempDir).count must beEqualTo(0L)
    }

    "report progress increasing monotonically" in new BaseScope {
      val reports = mutable.ArrayBuffer.empty[Double]
      def onProgress(d: Double): Unit = { reports.append(d) }

      await(sorter.sortIds(
        RecordSource(5, Source(immutable.Seq(
          Record(1, 4, Array(1.toByte, 2.toByte)),
          Record(2, 3, Array(4.toByte, 2.toByte)),
          Record(3, 2, Array(1.toByte, 1.toByte, 1.toByte)),
          Record(0, 1, Array(1.toByte, 2.toByte)),
          Record(4, 0, Array(3.toByte, 7.toByte))
        )).mapMaterializedValue(_ => Future.unit)),
        onProgress
      ))

      // Got these expected results by running the test, not by mathing it on
      // paper.
      reports.map(d => (d * 1000).round.toInt).toSeq must beEqualTo(Seq(286, 571, 657, 743, 829, 914, 1000))
    }
  }
}
