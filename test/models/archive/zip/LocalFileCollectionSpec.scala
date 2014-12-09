package models.archive.zip

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}
import scala.concurrent.Future

import models.archive.ArchiveEntry

class LocalFileCollectionSpec extends Specification with Mockito {

  "LocalFileCollection" should {

    "create LocalFileEntries with offset" in new LocalFileCollectionContext {
      localFiles.entries.map(_.offset) must be equalTo offsets

    }

    "combine streams of all entries" in new LocalFileCollectionContext {
      val streamSize = localFiles.entries.map(_.size).sum 
      
      val output = await(localFiles.stream.run(Iteratee.consume()))
      
      output.length must be equalTo streamSize.toInt
    }

    "compute size of all entries" in new LocalFileCollectionContext {
      localFiles.size must be equalTo numberOfEntries * (30 + 6 + fileSize)
    }
  }

  trait LocalFileCollectionContext extends Scope with FutureAwaits with DefaultAwaitTimeout {
    val numberOfEntries = 5
    val fileSize = 100
    val data = Array.tabulate(fileSize)(_.toByte)
    
    val archiveEntries = Seq.tabulate(numberOfEntries) { n =>
      ArchiveEntry(s"file-$n", fileSize, () => Future.successful(Enumerator(data)))
    }
    val offsets = archiveEntries.dropRight(1).foldLeft(Seq(0l)) { (o, e) => 
      o :+  o.last + (30 + e.name.size + fileSize) 
    }
    val localFiles = new LocalFileCollection(archiveEntries)    
  }

}


