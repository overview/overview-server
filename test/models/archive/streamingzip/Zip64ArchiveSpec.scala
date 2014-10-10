package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets._
import scala.util.Random
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.archive.ArchiveEntry

class Zip64ArchiveSpec extends Specification with Mockito {

  val EndOfArchiveSize = 56 + 20 + 22
  val LocalFileHeaderSize = 30
  val LocalExtraFieldSize = 20
  val CentralHeaderExtraFieldSize = 28
  val DataDescriptorSize = 24
  val FileHeaderSize = 46

  "Zip64Archive" should {

    "return actual size of archive" in new ArchiveContext {
      val output = readStream(archive.stream)

      output.length.toLong must be equalTo ArchiveSize(entries)
    }.pendingUntilFixed

    "return empty archive" in new EmptyArchiveContext {
      val output = readStream(archive.stream)

      output.length.toLong must be equalTo ArchiveSize(Seq.empty)
    }.pendingUntilFixed

    trait EmptyArchiveContext extends Scope {
      val archive = new Zip64Archive(Iterable.empty)

      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

    }

    trait ArchiveContext extends EmptyArchiveContext {
      val numberOfFiles = 10

      val fileInfo = for {
        (name, size) <- Seq.tabulate(numberOfFiles)(n => (s"file-$n", Random.nextInt(100)))
        fileData = new Array[Byte](size)
      } yield {
        Random.nextBytes(fileData)
        (name, size, fileData)
      }

      val entries = for {
        (name, size, data) <- fileInfo
      } yield ArchiveEntry(size, name, () => new ByteArrayInputStream(data))

      override val archive = new Zip64Archive(entries)

    }
  }
}



