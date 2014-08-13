package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.mockito.invocation.InvocationOnMock

class CreateDocumentsProcessSpec extends Specification with Mockito {

  "CreateDocumentsProcess" should {

    "create documents for first page of results" in {
      val documentData = Seq.tabulate(3)(n => (n.toLong, (s"document $n", createDocumentPages(n.toLong)))).toMap
      val documentSetId = 1l
      val documents = expectedDocuments(documentSetId, documentData)

      val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData)

      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId)

      firstStep.execute

      there was one(createDocumentsProcess.storage).writeDocuments(documents)
    }
  }

  private def createDocumentPages(fileId: Long): Iterable[(Int, String)] = Seq.tabulate(4)(n => (n, s"file $fileId page $n\n"))

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

class TestCreateDocumentsProcess(documentSetId: Long, documentData: Map[Long, (String, Iterable[(Int, String)])]) extends CreateDocumentsProcess with Mockito {

  private val files = for ((id, data) <- documentData) yield File(1, 1l, 1l, data._1, id)
  override val storage = smartMock[Storage]

  storage.findFilesQueryPage(documentSetId, 0) returns files
  storage.findFilePageData(anyLong) answers { p =>
    val n = p.asInstanceOf[Long]

    val pageData = documentData.get(n).map(d =>
      d._2.map(p => (p._1.toLong, p._1, Some(p._2)))).getOrElse(Seq.empty)

    pageData
  }
}  