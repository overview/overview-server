package org.overviewproject.jobhandler.filegroup.task

import akka.testkit.TestProbe
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import scala.concurrent.Future

import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.models.Document
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.tree.orm.File

class CreateDocumentsProcessSpec extends Specification with Mockito {

  "CreateDocumentsProcess" should {

    "create documents for first page of results" in new OneResultPageContext {
      createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref).execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(
        beLike[Iterable[Document]] { case docs: Iterable[Document] =>
          docs.map { d => (d.documentSetId, d.title, d.text, d.fileId, d.pageId) } must beEqualTo(Seq(
            (1, "file 0", "file 0 page 0\nfile 0 page 1\nfile 0 page 2\n", Some(0), None),
            (1, "file 1", "file 1 page 0\nfile 1 page 1\nfile 1 page 2\n", Some(1), None),
            (1, "file 2", "file 2 page 0\nfile 2 page 1\nfile 2 page 2\n", Some(2), None)
          ))
        }
      )
    }

    "create documents for all page results" in new TwoResultPagesContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute

      there was two(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(
        beLike[Iterable[Document]] { case docs: Iterable[Document] =>
          docs.map { d => (d.documentSetId, d.title, d.text, d.fileId, d.pageId) } must beOneOf(Seq(
            (1, "file 0", "file 0 page 0\nfile 0 page 1\nfile 0 page 2\n", Some(0), None),
            (1, "file 1", "file 1 page 0\nfile 1 page 1\nfile 1 page 2\n", Some(1), None),
            (1, "file 2", "file 2 page 0\nfile 2 page 1\nfile 2 page 2\n", Some(2), None)
          ), Seq(
            (1, "file 3", "file 3 page 0\nfile 3 page 1\nfile 3 page 2\n", Some(3), None),
            (1, "file 4", "file 4 page 0\nfile 4 page 1\nfile 4 page 2\n", Some(4), None),
            (1, "file 5", "file 5 page 0\nfile 5 page 1\nfile 5 page 2\n", Some(5), None)
          ))
        }
      )

      there was one(createDocumentsProcess.createDocumentsProcessStorage).saveDocumentCount(documentSetId)
      there was one(createDocumentsProcess.createDocumentsProcessStorage).refreshSortedDocumentIds(documentSetId)
      there was one(createDocumentsProcess.createDocumentsProcessStorage).deleteTempFiles(documentSetId)

      finalStep must haveClass[CreateDocumentsProcessComplete]
    }

    "create one document per page when splitDocuments is true" in new OneResultPageContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter.ref)
      firstStep.execute

      there was one(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(
        beLike[Iterable[Document]] { case docs: Iterable[Document] =>
          docs.map { d => (d.documentSetId, d.title, d.text, d.fileId, d.pageId) } must beEqualTo(Seq(
            (1, "file 0", "file 0 page 0\n", Some(0), Some(0)),
            (1, "file 0", "file 0 page 1\n", Some(0), Some(1)),
            (1, "file 0", "file 0 page 2\n", Some(0), Some(2)),
            (1, "file 1", "file 1 page 0\n", Some(1), Some(0)),
            (1, "file 1", "file 1 page 1\n", Some(1), Some(1)),
            (1, "file 1", "file 1 page 2\n", Some(1), Some(2)),
            (1, "file 2", "file 2 page 0\n", Some(2), Some(0)),
            (1, "file 2", "file 2 page 1\n", Some(2), Some(1)),
            (1, "file 2", "file 2 page 2\n", Some(2), Some(2))
          ))
        }
      )
    }

    "create one document per page for all query page results" in new TwoResultPagesContext {
      val firstStep = createDocumentsProcess.startCreateDocumentsTask(documentSetId, true, progressReporter.ref)
      val secondStep = firstStep.execute
      val thirdStep = secondStep.execute
      val finalStep = thirdStep.execute

      there was two(createDocumentsProcess.createDocumentsProcessStorage).writeDocuments(
        beLike[Iterable[Document]] { case docs: Iterable[Document] =>
          docs.map { d => (d.documentSetId, d.title, d.text, d.fileId, d.pageId) } must beOneOf(Seq(
            (1, "file 0", "file 0 page 0\n", Some(0), Some(0)),
            (1, "file 0", "file 0 page 1\n", Some(0), Some(1)),
            (1, "file 0", "file 0 page 2\n", Some(0), Some(2)),
            (1, "file 1", "file 1 page 0\n", Some(1), Some(0)),
            (1, "file 1", "file 1 page 1\n", Some(1), Some(1)),
            (1, "file 1", "file 1 page 2\n", Some(1), Some(2)),
            (1, "file 2", "file 2 page 0\n", Some(2), Some(0)),
            (1, "file 2", "file 2 page 1\n", Some(2), Some(1)),
            (1, "file 2", "file 2 page 2\n", Some(2), Some(2))
          ), Seq(
            (1, "file 3", "file 3 page 0\n", Some(3), Some(0)),
            (1, "file 3", "file 3 page 1\n", Some(3), Some(1)),
            (1, "file 3", "file 3 page 2\n", Some(3), Some(2)),
            (1, "file 4", "file 4 page 0\n", Some(4), Some(0)),
            (1, "file 4", "file 4 page 1\n", Some(4), Some(1)),
            (1, "file 4", "file 4 page 2\n", Some(4), Some(2)),
            (1, "file 5", "file 5 page 0\n", Some(5), Some(0)),
            (1, "file 5", "file 5 page 1\n", Some(5), Some(1)),
            (1, "file 5", "file 5 page 2\n", Some(5), Some(2))
          ))
        }
      )

      finalStep must haveClass[CreateDocumentsProcessComplete]
    }
    
    "add documents to search index" in new OneResultPageContext {
      createDocumentsProcess.startCreateDocumentsTask(documentSetId, false, progressReporter.ref)
        .execute
        .execute

      there was one(createDocumentsProcess.searchIndex).addDocumentSet(documentSetId)

      there was one(createDocumentsProcess.searchIndex).addDocuments(
        beLike[Iterable[Document]] { case docs: Iterable[Document] =>
          docs.map { d => (d.documentSetId, d.title, d.text, d.fileId, d.pageId) } must beEqualTo(Seq(
            (1, "file 0", "file 0 page 0\nfile 0 page 1\nfile 0 page 2\n", Some(0), None),
            (1, "file 1", "file 1 page 0\nfile 1 page 1\nfile 1 page 2\n", Some(1), None),
            (1, "file 2", "file 2 page 0\nfile 2 page 1\nfile 2 page 2\n", Some(2), None)
          ))
        }
      )

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
    def createDocumentPages(fileId: Long): Iterable[(Int, String)] = {
      Seq.tabulate(3)(n => (n, s"file $fileId page $n\n"))
    }
  }

  abstract class OneResultPageContext extends ActorSystemContext with CreateDocumentsProcessContext with Before {
    val pageSize = 3
    val documentData = Seq.tabulate(pageSize)(n => (n.toLong, s"file $n", createDocumentPages(n.toLong))).toIndexedSeq
    val documentSetId = 1l

    val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)

    var progressReporter: TestProbe = _
    override def before = {
      progressReporter = TestProbe()
    }
  }

  trait TwoResultPagesContext extends ActorSystemContext with CreateDocumentsProcessContext with Before {
    val pageSize = 3
    val documentData = Seq.tabulate(pageSize * 2)(n => (n.toLong, s"file $n", createDocumentPages(n.toLong))).toIndexedSeq
    val documentSetId = 1l

    val createDocumentsProcess = new TestCreateDocumentsProcess(documentSetId, documentData, pageSize)
    var progressReporter: TestProbe = _

    override def before = {
      progressReporter = TestProbe()
    }
  }
}

class TestCreateDocumentsProcess(
  documentSetId: Long,
  documentData: collection.immutable.IndexedSeq[(Long, String, Iterable[(Int, String)])],
  pageSize: Int
) extends CreateDocumentsProcess with Mockito {

  private val files = documentData
    .map { case (id, filename, _) => File(1, filename, "loc", 100L, "loc", 100L, id) }
  private val filesPage1 = files.take(pageSize)
  private val filesPage2 = files.drop(pageSize)

  override val searchIndex = smartMock[ElasticSearchIndexClient]

  searchIndex.addDocumentSet(documentSetId) returns Future.successful(())
  searchIndex.addDocuments(any) returns Future.successful(())
  searchIndex.refresh returns Future.successful(())
  
  override val createDocumentsProcessStorage = smartMock[CreateDocumentsProcessStorage]

  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 0) returns filesPage1.toSeq
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 1) returns filesPage2.toSeq
  createDocumentsProcessStorage.findFilesQueryPage(documentSetId, 2) returns Seq.empty

  createDocumentsProcessStorage.findFilePageText(anyLong) answers { p =>
    val n = p.asInstanceOf[Long]
    val d = documentData(n.toInt)
    d._3.map(p => PageText(p._1.toLong, p._1, Some(p._2)))
  }

  override protected def getDocumentIdGenerator(documentSetId: Long) = new TestDocumentIdGenerator(documentSetId)

  class TestDocumentIdGenerator(override val documentSetId: Long) extends DocumentIdGenerator {

    override protected def existingDocumentCount = 0
  }
}  
