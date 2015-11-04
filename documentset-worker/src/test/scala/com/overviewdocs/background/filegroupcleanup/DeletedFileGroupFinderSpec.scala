package com.overviewdocs.background.filegroupcleanup

import com.overviewdocs.test.DbSpecification

class DeletedFileGroupFinderSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    def go: Iterable[Long] = await(DeletedFileGroupFinder.indexIds)
  }

  "DeletedFileGroupFinder" should {
    "find a FileGroup" in new BaseScope {
      val fileGroup = factory.fileGroup(deleted=true)
      go must beEqualTo(Iterable(fileGroup.id))
    }

    "not find a non-deleted FileGroup" in new BaseScope {
      val fileGroup = factory.fileGroup(deleted=false)
      go must beEqualTo(Iterable())
    }

    "not find a deleted FileGroup that has an addToDocumentSetId" in new BaseScope {
      val documentSet = factory.documentSet()
      val fileGroup = factory.fileGroup(
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("en"),
        splitDocuments=Some(true),
        nFiles=Some(1),
        nBytes=Some(100L),
        nFilesProcessed=Some(0),
        nBytesProcessed=Some(0L),
        deleted=true
      )
      go must beEqualTo(Iterable())
    }
  }
}
