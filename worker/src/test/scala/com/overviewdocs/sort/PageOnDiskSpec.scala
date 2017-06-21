package com.overviewdocs.sort

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import java.io.{BufferedInputStream,DataInputStream}
import java.nio.file.{Files,Path}
import java.nio.{ByteBuffer,ByteOrder}
import org.specs2.mutable.Specification
import org.specs2.specification.{After,Scope}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.util.AwaitMethod
import com.overviewdocs.test.ActorSystemContext

class PageOnDiskSpec extends Specification with AwaitMethod {
  sequential

  trait BaseScope extends Scope with ActorSystemContext with After {
    implicit val ec = system.dispatcher
    implicit val mat = ActorMaterializer.create(system)

    val tempDir: Path = Files.createTempDirectory("PageOnDiskSpec")

    val records = immutable.Seq(
      Record(0, 1, Array(3.toByte, 2.toByte, 1.toByte)),
      Record(1, 2, Array(2.toByte, 1.toByte)),
      Record(2, 0, Array(3.toByte, 2.toByte, 1.toByte))
    )

    override def after = {
      import scala.compat.java8.FunctionConverters._

      Files.list(tempDir).forEach((Files.delete _).asJava)
      Files.deleteIfExists(tempDir)
    }
  }

  "PageOnDisk" should {
    "sortAndCreate" should {
      "create a temporary file" in new BaseScope {
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, records))
        Files.exists(pageOnDisk.path) must beEqualTo(true)
      }

      "count the number of records" in new BaseScope {
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, records))
        pageOnDisk.nRecords must beEqualTo(3)
      }

      "truncate huge records" in new BaseScope {
        val hugeRecord = Record(0, 0, new Array[Byte](PageOnDisk.maxNBytesOnDiskPerRecord + 100))
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, immutable.Seq(hugeRecord)))
        val bytes = Files.readAllBytes(pageOnDisk.path)
        val buf = ByteBuffer.wrap(bytes)
        buf.order(ByteOrder.nativeOrder)
        buf.limit must beEqualTo(PageOnDisk.maxNBytesOnDiskPerRecord)
        buf.getInt
        buf.getInt
        buf.getInt must beEqualTo(PageOnDisk.maxNBytesOnDiskPerRecord - 12)
      }

      "write sorted records to disk" in new BaseScope {
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, records))
        val bytes = Files.readAllBytes(pageOnDisk.path)
        val buf = ByteBuffer.wrap(bytes)
        buf.order(ByteOrder.nativeOrder)

        // First: Record 1 (smallest bytes) is written correctly
        buf.getInt must beEqualTo(1) // id: 1
        buf.getInt must beEqualTo(2) // canonicalPosition: 2
        buf.getInt must beEqualTo(2) // nBytes: 2
        Seq(buf.get, buf.get) must beEqualTo(Seq(2.toByte, 1.toByte))

        // Second: Record 2 (smallest canonicalPosition)
        buf.getInt must beEqualTo(2) // id: 2
        buf.getInt must beEqualTo(0) // canonicalPosition: 0
        buf.getInt must beEqualTo(3) // nBytes: 3
        Seq(buf.get, buf.get, buf.get) must beEqualTo(Seq(3.toByte, 2.toByte, 1.toByte))

        // Last: Record 0
        buf.getInt must beEqualTo(0) // id: 0
        buf.getInt must beEqualTo(1) // canonicalPosition: 1
        buf.getInt must beEqualTo(3) // nBytes: 3
        Seq(buf.get, buf.get, buf.get) must beEqualTo(Seq(3.toByte, 2.toByte, 1.toByte))

        // expect EOF
        buf.hasRemaining must beEqualTo(false)
      }
    }

    "toSourceDestructive" should {
      "read records from disk" in new BaseScope {
        // sloppy testing: rather than write to disk, we'll use .sortAndCreate()
        //
        // If one of the .sortAndCreate() tests fails, this one will as well.
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, records))

        val source = pageOnDisk.toSourceDestructive
        val result = await(source.toMat(Sink.seq) { (a: Future[Unit], b: Future[immutable.Seq[Record]]) =>
          for {
            _ <- a
            records <- b
          } yield records
        }.run)
        result.map(_.id) must beEqualTo(records.sorted.map(_.id))
        result.map(_.canonicalPosition) must beEqualTo(records.sorted.map(_.canonicalPosition))
        result.map(_.collationKey.toSeq) must beEqualTo(records.sorted.map(_.collationKey.toSeq))
      }

      "delete the source file" in new BaseScope {
        // sloppy testing: rather than write to disk, we'll use .sortAndCreate()
        //
        // If one of the .sortAndCreate() tests fails, this one will as well.
        val pageOnDisk = await(PageOnDisk.sortAndCreate(tempDir, records))

        val source = pageOnDisk.toSourceDestructive
        await(Sink.seq.runWith(source))
        Files.exists(pageOnDisk.path) must beEqualTo(false)
      }
    }
  }
}
