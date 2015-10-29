package com.overviewdocs.jobhandler.filegroup

import org.specs2.mutable.Specification

import com.overviewdocs.test.factories.{PodoFactory=>factory}

class AddDocumentsWorkGeneratorSpec extends Specification {
  "AddDocumentsWorkGenerator" should {
    val job = AddDocumentsJob(1L, 2L, 3L, "fr", true)

    def generator(nUploads: Int): AddDocumentsWorkGenerator = new AddDocumentsWorkGenerator(
      job,
      Seq.tabulate(nUploads) { i => factory.groupedFileUpload(fileGroupId=3L, name=s"file $i") }
    )

    import AddDocumentsWorkGenerator._

    "return FinishJobWork when initialized with an empty set of uploads" in {
      generator(0).nextWork must beEqualTo(FinishJobWork)
    }

    "return a ProcessFileWork for each file" in {
      val g = generator(2)
      g.nextWork must beEqualTo(ProcessFileWork(g.uploads(0)))
      g.nextWork must beEqualTo(ProcessFileWork(g.uploads(1)))
    }

    "return a NoWorkForNow when tapped out" in {
      val g = generator(1)
      g.nextWork
      g.nextWork must beEqualTo(NoWorkForNow)
    }

    "return a NoWorkForNow after some (but not all) required work is finished" in {
      val g = generator(2)
      g.nextWork
      g.nextWork
      g.markDoneOne
      g.nextWork must beEqualTo(NoWorkForNow)
    }

    "return a ProcessFileWork even after markOneDone" in {
      val g = generator(2)
      g.nextWork
      g.markDoneOne
      g.nextWork must beEqualTo(ProcessFileWork(g.uploads(1)))
    }

    "return a FinishJobWork after all ProcessFileWork is finished" in {
      val g = generator(1)
      g.nextWork
      g.markDoneOne
      g.nextWork must beEqualTo(FinishJobWork)
    }

    "skip queued work" in {
      val g = generator(2)
      g.skipRemainingFileWork
      g.nextWork must beEqualTo(FinishJobWork)
    }

    "postpone FinishJobWork after skipping but before all markDoneOne calls have come in" in {
      val g = generator(2)
      g.nextWork
      g.skipRemainingFileWork
      g.nextWork must beEqualTo(NoWorkForNow)
    }

    "return FinishJobWork after skipRemainingFileWork+markDoneOne" in {
      val g = generator(2)
      g.nextWork
      g.skipRemainingFileWork
      g.markDoneOne
      g.nextWork must beEqualTo(FinishJobWork)
    }
  }
}
