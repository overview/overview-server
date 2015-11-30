package com.overviewdocs.clustering

import com.overviewdocs.models.tables.Documents
import com.overviewdocs.test.DbSpecification

class DocumentUpdaterSpec extends DbSpecification {
  "DocumentUpdater" should {
    "defer flushing" in new DbScope {
      val updater = new DocumentUpdater
      val documentSet = factory.documentSet()
      val document1 = factory.document(documentSetId=documentSet.id, keywords=Seq("unchanged"))
      val document2 = factory.document(documentSetId=documentSet.id, keywords=Seq("unchanged"))
      updater.blockingUpdateKeywordsAndFlushIfNeeded(document1.id, Seq("foo", "bar"))
      updater.blockingUpdateKeywordsAndFlushIfNeeded(document2.id, Seq("bar", "baz"))

      import database.api._
      blockingDatabase.seq(Documents).map(_.keywords) must beEqualTo(Seq(Seq("unchanged"), Seq("unchanged")))
    }

    "update keywords" in new DbScope {
      val updater = new DocumentUpdater
      val documentSet = factory.documentSet()
      val document1 = factory.document(documentSetId=documentSet.id, keywords=Seq("unchanged"))
      val document2 = factory.document(documentSetId=documentSet.id, keywords=Seq("unchanged"))
      updater.blockingUpdateKeywordsAndFlushIfNeeded(document1.id, Seq("foo", "bar"))
      updater.blockingUpdateKeywordsAndFlushIfNeeded(document2.id, Seq("bar", "baz"))
      updater.blockingFlush

      import database.api._
      blockingDatabase.option(Documents.filter(_.id === document1.id)).map(_.keywords) must beSome(Seq("foo", "bar"))
      blockingDatabase.option(Documents.filter(_.id === document2.id)).map(_.keywords) must beSome(Seq("bar", "baz"))
    }
  }
}
