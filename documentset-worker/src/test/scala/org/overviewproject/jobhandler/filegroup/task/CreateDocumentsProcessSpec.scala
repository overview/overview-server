package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.mockito.invocation.InvocationOnMock
import org.specs2.specification.Scope
import org.overviewproject.util.SearchIndex
import org.overviewproject.util.DocumentSetIndexingSession
import scala.concurrent.Future
import akka.actor.ActorRef

class CreateDocumentsProcessSpec extends Specification with Mockito {

  "CreateDocumentsProcess" should {

    "create documents for first page of results" in new OneResultPageContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter)

      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documents)
    }

    "create documents for all page results" in new TwoResultPagesContext {

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute

      there was atLeastOne(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsPage1) //andThen
      there was atLeastOne(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsPage2)

      there was one(createDocumentsProcess.createDocumentsProcessStorage).saveDocumentCount(documentSetId)
      there was one(createDocumentsProcess.createDocumentsProcessStorage).deleteTempFiles(documentSetId)

      finalStep must haveClass[CreateDocumentsProcessComplete]
    }

    "create one document per page when splitDocuments is true" in new OneResultPageContext {
      val documentsFromPages = expectedDocumentsPerPage(documentSetId, documentData)

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter)
      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsFromPages)
    }

    "add documents to search index" in new OneResultPageContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter)

      val nextStep = firstStep.execute

      there was one(createDocumentsProcess.searchIndex).startDocumentSetIndexingSession(documentSetId)

      there were pageSize.times(createDocumentsProcess.indexingSession).indexDocument(anyLong, anyLong, anyString, any, any)

      nextStep.execute

      there was one(createDocumentsProcess.indexingSession).complete
      there was one(createDocumentsProcess.indexingSession).requestsComplete
    }

    
    trait OneResultPageContext extends Scope {
      val pageSize = 10
      val documentData = Seq.tabulate(pageSize)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
      val documentSetId = 1l
      val documents = expectedDocuments(documentSetId, documentData)

      val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)

      val progressReporter: ActorRef = null
    }

    trait TwoResultPagesContext extends Scope {
      val pageSize = 5
      val documentData = Seq.tabulate(pageSize * 2)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
      val documentSetId = 1l
      val documents = expectedDocuments(documentSetId, documentData)
      val documentsPage1 = documents.take(pageSize)
      val documentsPage2 = documents.drop(pageSize)

      val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)
      val progressReporter: ActorRef = null
    }
  }

  private def createDocumentPages(fileId: Long): Iterable[(Int, String)] =
    Seq.tabulate(4)(n => (n, s"file $fileId page $n\n"))

  private def expectedDocuments(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])]): Iterable[Document] = {
    var documentId = 0

    for {
      (fileId, data) <- documentData
    } yield {
      val documentText = data._2.map(_._2).mkString

      documentId += 1
      Document(documentSetId,
        id = (documentSetId << 32) | documentId,
        title = Some(data._1),
        text = Some(documentText),
        fileId = Some(fileId))
    }
  }

  private def expectedDocumentsPerPage(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])]): Iterable[Document] = {
    var documentId = 0

    for {
      (fileId, data) <- documentData
      (documentTitle, pages) = data
      (pageNumber, documentText) <- pages
    } yield {
      documentId += 1
      Document(documentSetId,
        id = (documentSetId << 32) | documentId,
        title = Some(documentTitle),
        text = Some(documentText),
        fileId = Some(fileId),
        pageNumber = Some(pageNumber),
        pageId = Some(pageNumber))
    }
  }
}

class TestCreateDocumentsProcess(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])], pageSize: Int) extends CreateDocumentsProcess with Mockito {

  private val files = for ((id, data) <- documentData) yield File(1, 1l, 1l, data._1, id)
  private val filesPage1 = files.take(pageSize)
  private val filesPage2 = files.drop(pageSize)

  val indexingSession = smartMock[DocumentSetIndexingSession]
  indexingSession.requestsComplete returns Future.successful()
  
  override val searchIndex = smartMock[SearchIndex]

  searchIndex.startDocumentSetIndexingSession(documentSetId) returns indexingSession

  override val createDocumentsProcessStorage = smartMock[CreateDocumentsProcessStorage]

  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 0) returns filesPage1.toSeq
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 1) returns filesPage2.toSeq
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 2) returns Seq.empty

  createDocumentsProcessStorage.findFilePageText(anyLong) answers { p =>
    val n = p.asInstanceOf[Long]

    val pageData = documentData.get(n).map(d =>
      d._2.map(p => PageText(p._1.toLong, p._1, Some(p._2)))).getOrElse(Seq.empty)

    pageData
  }

  override protected def getDocumentIdGenerator(documentSetId: Long) = new TestDocumentIdGenerator(documentSetId)

  class TestDocumentIdGenerator(override val documentSetId: Long) extends DocumentIdGenerator {

    override protected def largestExistingId = 0
  }
}  