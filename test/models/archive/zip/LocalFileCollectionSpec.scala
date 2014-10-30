package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.archive.ArchiveEntry
import org.specs2.mock.Mockito
import java.io.InputStream
import models.archive.StreamReader
import java.io.ByteArrayInputStream

class LocalFileCollectionSpec extends Specification with Mockito {

  "LocalFileCollection" should {

    "create LocalFileEntries with offset" in new LocalFileCollectionContext {
      localFiles.entries.map(_.offset) must be equalTo offsets

    }

    "combine streams of all entries" in new LocalFileCollectionContext {
      val streamSize = localFiles.entries.map(_.size).sum 
      
      val output = readStream(localFiles.stream)
      
      output.length must be equalTo streamSize.toInt
    }

    "compute size of all entries" in new LocalFileCollectionContext {
      localFiles.size must be equalTo numberOfEntries * (30 + 6 + fileSize)
    }
  }

  trait LocalFileCollectionContext extends Scope with StreamReader {
    val numberOfEntries = 5
    val fileSize = 100
    val data = Array.tabulate(fileSize)(_.toByte)
    
    val archiveEntries = Seq.tabulate(numberOfEntries)(n => ArchiveEntry(s"file-$n",
        fileSize, () => new ByteArrayInputStream(data)))
    val offsets = archiveEntries.dropRight(1).foldLeft(Seq(0l))((o, e) => 
      o :+  o.last + (30 + e.name.size + fileSize) 
    )
    val localFiles = new LocalFileCollection(archiveEntries)    
  }

}


