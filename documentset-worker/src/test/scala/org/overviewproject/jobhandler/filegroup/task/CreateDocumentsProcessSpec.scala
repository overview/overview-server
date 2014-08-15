package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.mockito.invocation.InvocationOnMock

class CreateDocumentsProcessSpec extends Specification with Mockito {

  "CreateDocumentsProcess" should {

    "create documents for first page of results" in {
      val pageSize = 10
      val documentData = Seq.tabulate(pageSize)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
      val documentSetId = 1l
      val documents = expectedDocuments(documentSetId, documentData)

      val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false)

      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documents)
    }

    "create documents for all page results" in {
      val pageSize = 5
      val documentData = Seq.tabulate(pageSize * 2)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
      val documentSetId = 1l
      val documents = expectedDocuments(documentSetId, documentData)
      val documentsPage1 = documents.take(pageSize)
      val documentsPage2 = documents.drop(pageSize)

      val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute

      there was atLeastOne(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsPage1) //andThen
      there was atLeastOne(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(documentsPage2)

      finalStep must haveClass[CreateDocumentsProcessComplete]
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
}

class TestCreateDocumentsProcess(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])], pageSize: Int) extends CreateDocumentsProcess with Mockito {

  private val files = for ((id, data) <- documentData) yield File(1, 1l, 1l, data._1, id)
  private val filesPage1 = files.take(pageSize)
  private val filesPage2 = files.drop(pageSize)

  override val createDocumentsProcessStorage = smartMock[CreateDocumentsProcessStorage]

  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 0) returns filesPage1
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 1) returns filesPage2
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 2) returns Seq.empty

  createDocumentsProcessStorage.findFilePageData(anyLong) answers { p =>
    val n = p.asInstanceOf[Long]

    val pageData = documentData.get(n).map(d =>
      d._2.map(p => (p._1.toLong, p._1, Some(p._2)))).getOrElse(Seq.empty)

    pageData
  }

  override protected def getDocumentIdGenerator(documentSetId: Long) = new TestDocumentIdGenerator(documentSetId)

  class TestDocumentIdGenerator(override val documentSetId: Long) extends DocumentIdGenerator {

    override protected def largestExistingId = 0
  }
}  