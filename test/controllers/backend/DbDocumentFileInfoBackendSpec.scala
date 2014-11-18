package controllers.backend

import models.archive.DocumentViewInfo

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

      there was one(backend.mockFactory).fromFile((filename, oid, size))
    }

    "find info for text documents" in new TextScope {
      val infos = await(backend.indexDocumentViewInfos(documentSet.id))

      there was one(backend.mockFactory).fromText((filename, "", document.id, pageNumber, text.size))
    }
  }

  trait DocumentScope extends DbScope {
    val backend = new TestDbDocumentFileInfoBackend(session)

    val filename = "filename"
    val documentSet = factory.documentSet()
  }

  trait SplitDocumentsScope extends DocumentScope {
    val numberOfPages = 2

    val file = factory.file(name = filename)
    val pages = Seq.tabulate(numberOfPages)(n => factory.page(pageNumber = (n + 1), fileId = file.id))
    val documents = pages.map(p =>
      factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id),
        pageId = Some(p.id), pageNumber = Some(p.pageNumber)))

    def pagesWereUsedInViewInfoCreation = pages.map { p =>
      there was one(backend.mockFactory).fromPage((filename, p.pageNumber, p.id, p.data.get.length))
    }

  }

  trait FileScope extends DocumentScope {
    val oid = 1234l
    val size = 456l

    val file = factory.file(name = filename, viewOid = oid, viewSize = Some(size))
    val document = factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id))
  }

  trait TextScope extends DocumentScope {
    val text = "document text"
    val pageNumber: Option[Int] = None
    
    val document = factory.document(
      documentSetId = documentSet.id,
      title = filename,
      text = text)
  }

  class TestDbDocumentFileInfoBackend(session: Session) extends TestDbBackend(session) with DbDocumentFileInfoBackend {
    override val documentViewInfoFactory = smartMock[DocumentViewInfoFactory]
    val mockFactory = documentViewInfoFactory
    mockFactory.fromPage(any) returns smartMock[DocumentViewInfo]
    mockFactory.fromFile(any) returns smartMock[DocumentViewInfo]
    mockFactory.fromText(any) returns smartMock[DocumentViewInfo]
  }
}