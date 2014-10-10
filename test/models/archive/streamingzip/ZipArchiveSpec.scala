package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream
import scala.util.Random
import java.io.ByteArrayInputStream
import models.archive.ArchiveEntry

class ZipArchiveSpec extends Specification {

  "ZipArchive" should {

    
    "return archive that is the correct size" in new ArchiveContext {
      val output = readStream(archive.stream)
      
      output.length.toLong must be equalTo ArchiveSize(entries)
    }
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
    } yield ArchiveEntry(size, name, () => new ByteArrayInputStream(data))

    val archive = new ZipArchive(entries)

    def readStream(stream: InputStream): Array[Byte] =
      Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

  }
}