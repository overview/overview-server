package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import models.archive.ArchiveEntry
import models.archive.ArchiveEntryCollection

class ZipArchiveSpec extends Specification {

  "ZipArchive" should {

    "compute size of archive" in new ZipArchiveContext {
      archive.size must be equalTo archiveSize
    }

    "write to archive to stream" in new ZipArchiveContext {
      val output = await(archive.stream.run(Iteratee.consume()))
      
      output.length must be equalTo archiveSize
    }
  }

  trait ZipArchiveContext extends Scope with FutureAwaits with DefaultAwaitTimeout {
    val fileNameLength = 10
    val numberOfEntries = 10
    val fileSize = 2048

    val archiveSize = numberOfEntries * (30 + fileNameLength + fileSize + 46 + fileNameLength) + 22

    val data = Array.fill(fileSize)(0xda.toByte)

    val entries = Seq.tabulate(numberOfEntries)(n => ArchiveEntry(f"file-$n%05d", fileSize, fileStream _))

    val archive = new ZipArchive(new ArchiveEntryCollection(entries))

    def fileStream = Future.successful(Enumerator(data))
  }
}
