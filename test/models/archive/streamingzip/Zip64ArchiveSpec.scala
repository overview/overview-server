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

    "return size of an empty archive" in {
      val archive = new Zip64Archive(Iterable.empty)

      archive.archiveSize must be equalTo (EndOfArchiveSize)
    }

    "return size of an archive with files" in {
      val entrySize = 100
      val numberOfEntries = 10
      val fileNameSize = 5
      val archiveEntries = Seq.tabulate(numberOfEntries)(n =>
        ArchiveEntry(entrySize, s"name$n", mock[InputStream]))

      val archive = new Zip64Archive(archiveEntries)

      archive.archiveSize must be equalTo (numberOfEntries * (
        LocalFileHeaderSize + LocalExtraFieldSize + DataDescriptorSize
        + fileNameSize + entrySize +
        FileHeaderSize + CentralHeaderExtraFieldSize + fileNameSize) + EndOfArchiveSize)
    }

    "return actual size of archive" in new ArchiveContext {
      val output = readStream(archive.stream)

      archive.archiveSize must be equalTo output.length
    }


    trait ArchiveContext extends Scope {
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
        fileStream = new ByteArrayInputStream(data)
      } yield ArchiveEntry(size, name, fileStream)

      val archive = new Zip64Archive(entries)

      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

    }
  }
}



