package models.archive

import org.specs2.specification.Scope
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import java.io.InputStream



trait ViewInfoSpecification extends Specification with Mockito {

    trait ArchiveEntryFactoryContext extends Scope {
    val Pdf = ".pdf"
    def originalName = "file.doc"
    def cleanName = originalName.replaceAll("\\.", "_")

    val size = 3418913

    def matchesEntryParams(name: String, size: Long, oid: Long)(e: ArchiveEntry) = {
      e.name must be equalTo name
      e.size must be equalTo size

      val s = e.data()
      streamWasCreatedFromId(oid)
    }

    def streamWasCreatedFromId(id: Long): MatchResult[Any]

  }


  class TestArchiveEntryFactory(id: Long) extends ArchiveEntryFactory {

    override protected val storage = smartMock[Storage]
    storage.largeObjectInputStream(id) returns smartMock[InputStream]

    storage.pageDataStream(id) returns Some(smartMock[InputStream])
    def mockStorage = storage
  }

}