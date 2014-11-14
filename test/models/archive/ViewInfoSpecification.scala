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

    val size: Long = 3418913

    def matchParameters(name: String, size: Long, oid: Long) = { (e: ArchiveEntry) =>
      val s = e.data()
      (e.name must be equalTo name) and
      (e.size must be equalTo size) and
      streamWasCreatedFromId(oid)
    }
    
    def streamWasCreatedFromId(id: Long): MatchResult[Any]

  }


}
