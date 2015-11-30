package com.overviewdocs.documentcloud

import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.models.{DocumentCloudImport,DocumentCloudImportIdList}
import com.overviewdocs.models.tables.{DocumentCloudImports,DocumentCloudImportIdLists}
import com.overviewdocs.test.DbSpecification

class IdListFetcherSpec extends DbSpecification with Mockito {
  trait BaseScope extends DbScope {
    val documentSet = factory.documentSet()
    val server = smartMock[DocumentCloudServer]
    lazy val dcImport: DocumentCloudImport = factory.documentCloudImport(documentSetId=documentSet.id)
    lazy val subject = new IdListFetcher(dcImport, server, 3)

    val row = IdListRow("id","title",2,"full","template{page}","access")
    def encodedRows(n: Int) = Seq.fill(n)("id\u001ftitle\u001f2\u001ffull\u001ftemplate{page}\u001faccess").mkString("\u001e")

    import database.api._

    def dbImport = blockingDatabase.option(DocumentCloudImports).get
    def dbLists = blockingDatabase.seq(DocumentCloudImportIdLists.sortBy(_.id).map(_.createAttributes))
  }

  "IdListFetcher" should {
    "succeed if DocumentCloud reports 0 documents" in new BaseScope {
      server.getIdList0(any, any, any) returns Future.successful(Right((IdList(Seq()), 0)))
      subject.run must beEqualTo(IdListFetcher.Success(1, 0, 0)).await
      dbImport.nIdListsTotal must beSome(1)
      dbImport.nIdListsFetched must beEqualTo(1)
      dbLists must beEqualTo(Seq(
        DocumentCloudImportIdList.CreateAttributes(dcImport.id, 0, "", 0, 0)
      ))
    }

    "succeed with one page" in new BaseScope {
      server.getIdList0(any, any, any) returns Future.successful(Right((IdList(Seq.fill(3)(row)), 3)))
      subject.run must beEqualTo(IdListFetcher.Success(1, 3, 6)).await
      dbImport.nIdListsTotal must beSome(1)
      dbImport.nIdListsFetched must beEqualTo(1)
      dbLists must beEqualTo(Seq(
        DocumentCloudImportIdList.CreateAttributes(dcImport.id, 0, encodedRows(3), 3, 6)
      ))
    }

    "succeed with two pages" in new BaseScope {
      server.getIdList0(any, any, any) returns Future.successful(Right((IdList(Seq.fill(3)(row)), 4)))
      server.getIdList(any, any, any, any) returns Future.successful(Right(IdList(Seq(row))))
      subject.run must beEqualTo(IdListFetcher.Success(2, 4, 8)).await
      dbImport.nIdListsTotal must beSome(2)
      dbImport.nIdListsFetched must beEqualTo(2)
      dbLists must beEqualTo(Seq(
        DocumentCloudImportIdList.CreateAttributes(dcImport.id, 0, encodedRows(3), 3, 6),
        DocumentCloudImportIdList.CreateAttributes(dcImport.id, 1, encodedRows(1), 1, 2)
      ))
    }

    "skip to end when resuming and we're already done" in new BaseScope {
      override lazy val dcImport = factory.documentCloudImport(
        documentSetId=documentSet.id,
        nIdListsTotal=Some(2)
      )
      factory.documentCloudImportIdList(1, dcImport.id, 0, encodedRows(3), 3, 6)
      factory.documentCloudImportIdList(2, dcImport.id, 1, encodedRows(1), 1, 2)
      subject.run must beEqualTo(IdListFetcher.Success(2, 4, 8)).await
      dbImport.nIdListsFetched must beEqualTo(2)
    }

    "resume without re-fetching pages" in new BaseScope {
      override lazy val dcImport = factory.documentCloudImport(
        documentSetId=documentSet.id,
        nIdListsTotal=Some(2)
      )
      factory.documentCloudImportIdList(1, dcImport.id, 0, encodedRows(3), 3, 6)
      server.getIdList(any, any, any, any) returns Future.successful(Right(IdList(Seq(row))))
      subject.run must beEqualTo(IdListFetcher.Success(2, 4, 8)).await
      dbImport.nIdListsTotal must beSome(2)
      dbImport.nIdListsFetched must beEqualTo(2)
      dbLists(1) must beEqualTo(
        DocumentCloudImportIdList.CreateAttributes(dcImport.id, 1, encodedRows(1), 1, 2)
      )
    }

    "report an error" in new BaseScope {
      server.getIdList0(any, any, any) returns Future.successful(Left("error"))
      subject.run must beEqualTo(IdListFetcher.Stop("error")).await
    }

    "report an error halfway through" in new BaseScope {
      server.getIdList0(any, any, any) returns Future.successful(Right((IdList(Seq.fill(3)(row)), 9)))
      server.getIdList(any, any, any, any) returns Future.successful(Left("error"))
      subject.run must beEqualTo(IdListFetcher.Stop("error")).await
      dbLists.length must beEqualTo(1)
    }
  }
}
