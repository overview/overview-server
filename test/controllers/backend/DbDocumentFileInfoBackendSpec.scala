package controllers.backend

import models.archive.{FileViewInfo,PageViewInfo,TextViewInfo}

class DbDocumentFileInfoBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbDocumentFileInfoBackend with org.overviewproject.database.DatabaseProvider
    val filename = "filename"
    val documentSet = factory.documentSet()
  }

  "DbDocumentFileInfoBackend" should {
    "find info for pages" in new BaseScope {
      val numberOfPages = 2

      val file = factory.file(name = filename)
      val pages = Seq.tabulate(numberOfPages)(n => factory.page(
        pageNumber = (n + 1),
        fileId = file.id,
        dataLocation = "loca:tion:" + n,
        dataSize = 123 * (n + 1)
      ))
      val documents = pages.map(p =>
        factory.document(
          documentSetId = documentSet.id,
          title = filename,
          fileId = Some(file.id),
          pageId = Some(p.id),
          pageNumber = Some(p.pageNumber)
        )
      )

      backend.indexDocumentViewInfos(documentSet.id) must beEqualTo(Seq(
        PageViewInfo(filename, 1, "loca:tion:0", 123),
        PageViewInfo(filename, 2, "loca:tion:1", 246)
      )).await
    }

    "find info for files" in new BaseScope {
      val location = "location:view"
      val size = 456l

      val file = factory.file(name = filename, viewLocation = location, viewSize = size)
      val document = factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id))

      backend.indexDocumentViewInfos(documentSet.id) must beEqualTo(Seq(
        FileViewInfo(filename, location, size)
      )).await
    }

    "find info for text documents" in new BaseScope {
      val document = factory.document(
        documentSetId = documentSet.id,
        title = filename,
        text = "document text"
      )

      await(backend.indexDocumentViewInfos(documentSet.id)) must beEqualTo(Seq(
        TextViewInfo(filename, "", document.id, None, "document text".size)
      ))
    }
  }
}
