package controllers.backend

import models.archive.FileViewInfo
import models.archive.PageViewInfo

import org.specs2.mock.Mockito
import slick.jdbc.JdbcBackend.Session

class DbDocumentFileInfoBackendSpec extends DbBackendSpecification with Mockito {

  "DbDocumentFileInfoBackend" should {

    "find info for pages" in new SplitDocumentsScope {
      val infos = await(backend.indexDocumentViewInfos(documentSet.id))
      
      pagesWereUsedInViewInfoCreation
    }

    "find info for files" in new FileScope {
      val infos = await(backend.indexDocumentViewInfos(documentSet.id))

      there was one(backend.mockFactory).fromFile1((filename, oid, size))
    }
  }

  trait SplitDocumentsScope extends DbScope {
    val backend = new TestDbDocumentFileInfoBackend(session)

    val filename = "filename"
    val numberOfPages = 2
    val documentSet = factory.documentSet()
    val file = factory.file(name = filename)
    val pages = Seq.tabulate(numberOfPages)(n => factory.page(pageNumber = (n + 1), fileId = file.id))
    val documents = pages.map(p =>
      factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id),
        pageId = Some(p.id), pageNumber = Some(p.pageNumber)))

    def pagesWereUsedInViewInfoCreation = pages.map { p =>
      there was one(backend.mockFactory).fromPage1((filename, p.pageNumber, p.id, p.data.get.length))
    }

  }

  trait FileScope extends DbScope {
    val backend = new TestDbDocumentFileInfoBackend(session)

    val filename = "filename"
    val oid = 1234l
    val size = 456l

    val documentSet = factory.documentSet()
    val file = factory.file(name = filename, viewOid = oid, viewSize = Some(size))
    val document = factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id))

  }

  class TestDbDocumentFileInfoBackend(session: Session) extends TestDbBackend(session) with DbDocumentFileInfoBackend {
    override val documentViewInfoFactory = smartMock[DocumentViewInfoFactory]
    val mockFactory = documentViewInfoFactory
    mockFactory.fromPage(any) returns smartMock[PageViewInfo]
    mockFactory.fromFile(any) returns smartMock[FileViewInfo]
  }
}