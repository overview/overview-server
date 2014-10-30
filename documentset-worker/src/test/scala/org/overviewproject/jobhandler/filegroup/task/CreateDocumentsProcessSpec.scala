package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Promise
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification

class CreateDocumentsProcessSpec extends Specification with Mockito {

  "CreateDocumentsProcess" should {

    "create documents for first page of results" in new OneResultPageContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref)

      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documents)
    }

    "create documents for all page results" in new TwoResultPagesContext {

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref)
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

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter.ref)
      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsFromPages)
    }

    "create one document per page for all query page results" in new TwoResultPagesContext {
      val documentsFromPages = expectedDocumentsPerPage(documentSetId, documentData)

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter.ref)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute
      
      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsFromPages.take(4 * pageSize))
      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsFromPages.drop(4 * pageSize))

      finalStep must haveClass[CreateDocumentsProcessComplete]
    }
    
    "add documents to search index" in new OneResultPageContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref)

      there was one(createDocumentsProcess.searchIndex).addDocumentSet(documentSetId)

      val nextStep = firstStep.execute
      nextStep.execute
      
      there was one(createDocumentsProcess.searchIndex).addDocuments(documents.take(pageSize))
      there was one(createDocumentsProcess.searchIndex).refresh

    }

    "send notification to progress reporter for each file processed" in new OneResultPageContext {
      val fileIds = documentData.map(_._1)
      val startTaskMessages = fileIds.map(id => StartTask(documentSetId, id)).toSeq
      val completeTaskMessages = fileIds.map(id => CompleteTask(documentSetId, id)).toSeq

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter.ref)
      firstStep.execute

      progressReporter.receiveN(2 * pageSize) must containTheSameElementsAs(startTaskMessages ++ completeTaskMessages)
    }
  }

  trait CreateDocumentsProcessContext {
    def createDocumentPages(fileId: Long): Iterable[(Int, String)] =
      Seq.tabulate(4)(n => (n, s"file $fileId page $n\n"))

    def expectedDocuments(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])]): Iterable[Document] = {
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

    def expectedDocumentsPerPage(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])]): Iterable[Document] = {
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

  abstract class OneResultPageContext extends ActorSystemContext with CreateDocumentsProcessContext with Before {
    val pageSize = 10
    val documentData = Seq.tabulate(pageSize)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
    val documentSetId = 1l
    val documents = expectedDocuments(documentSetId, documentData)

    val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)

    var progressReporter: TestProbe = _
    override def before = {
      progressReporter = TestProbe()
    }

  }

  trait TwoResultPagesContext extends ActorSystemContext with CreateDocumentsProcessContext with Before {
    val pageSize = 5
    val documentData = Seq.tabulate(pageSize * 2)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
    val documentSetId = 1l
    val documents = expectedDocuments(documentSetId, documentData)
    val documentsPage1 = documents.take(pageSize)
    val documentsPage2 = documents.drop(pageSize)

    val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)
    var progressReporter: TestProbe = _

    override def before = {
      progressReporter = TestProbe()
    }

  }

}

class TestCreateDocumentsProcess(
    documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])], pageSize: Int)
    extends CreateDocumentsProcess with Mockito {

  private val files = for ((id, data) <- documentData) yield File(1, 1l, 1l, data._1, Some(100), Some(100), id)
  private val filesPage1 = files.take(pageSize)
  private val filesPage2 = files.drop(pageSize)

  override val searchIndex = smartMock[ElasticSearchIndexClient]

  searchIndex.addDocumentSet(documentSetId) returns Promise.successful().future
  searchIndex.addDocuments(any) returns Promise.successful().future
  searchIndex.refresh returns Promise.successful().future
  
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

    override protected def existingDocumentCount = 0
  }
}  