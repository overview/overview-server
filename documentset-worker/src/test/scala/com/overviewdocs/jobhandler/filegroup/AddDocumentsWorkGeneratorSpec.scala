package com.overviewdocs.jobhandler.filegroup

import java.time.Instant
import org.specs2.mutable.Specification

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class AddDocumentsWorkGeneratorSpec extends Specification {
  "AddDocumentsWorkGenerator" should {
    def generator(nUploads: Int): AddDocumentsWorkGenerator = {
      val fileGroup = factory.fileGroup(
        id=1L,
        addToDocumentSetId=Some(2L),
        lang=Some("fr"),
        splitDocuments=Some(true),
        nFiles=Some(nUploads),
        nBytes=Some(100 * Seq.tabulate(nUploads)(_ + 1).sum)
      )
      val uploads = Seq.tabulate(nUploads) { i =>
        factory.groupedFileUpload(fileGroupId=3L, name=s"file $i", size=100 * (i + 1))
      }

      new AddDocumentsWorkGenerator(fileGroup, uploads)
    }

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
      g.markWorkDone(g.uploads(0))
      g.nextWork must beEqualTo(NoWorkForNow)
    }

    "return a ProcessFileWork even after markOneDone" in {
      val g = generator(2)
      g.nextWork
      g.markWorkDone(g.uploads(0))
      g.nextWork must beEqualTo(ProcessFileWork(g.uploads(1)))
    }

    "return a FinishJobWork after all ProcessFileWork is finished" in {
      val g = generator(1)
      g.nextWork
      g.markWorkDone(g.uploads(0))
      g.nextWork must beEqualTo(FinishJobWork)
    }

    "skip queued work" in {
      val g = generator(2)
      g.skipRemainingFileWork
      g.nextWork must beEqualTo(FinishJobWork)
    }

    "postpone FinishJobWork after skipping but before all markWorkDone calls have come in" in {
      val g = generator(2)
      g.nextWork
      g.skipRemainingFileWork
      g.nextWork must beEqualTo(NoWorkForNow)
    }

    "return FinishJobWork after skipRemainingFileWork+markWorkDone" in {
      val g = generator(2)
      val w1 = g.nextWork
      g.skipRemainingFileWork
      g.markWorkDone(g.uploads(0))
      g.nextWork must beEqualTo(FinishJobWork)
    }

    "progress" should {
      "have nFiles=nBytes=0 on start" in {
        val g = generator(2)
        g.progress must beEqualTo(Progress(0, 0, Instant.MAX))
      }

      "have nFiles=nBytes=0 after starting jobs" in {
        val g = generator(2)
        g.nextWork
        g.nextWork
        g.progress must beEqualTo(Progress(0, 0L, Instant.MAX))
      }

      "set nFiles and nBytes when completing a job" in {
        val g = generator(2)
        g.nextWork
        g.markWorkDone(g.uploads(0))
        g.progress.nFilesProcessed must beEqualTo(1)
        g.progress.nBytesProcessed must beEqualTo(100)
        g.progress.estimatedCompletionTime mustNotEqual(Instant.MAX)
      }

      "have nFiles=nBytes=0 on start of an empty FileGroup" in {
        val g = generator(0)
        g.progress must beEqualTo(Progress(0, 0, Instant.MAX))
      }

      "be completed when the FileGroup is completed" in {
        val g = generator(2)
        g.nextWork
        g.nextWork
        g.markWorkDone(g.uploads(0))
        g.markWorkDone(g.uploads(1))
        g.progress.nFilesProcessed must beEqualTo(2)
        g.progress.nBytesProcessed must beEqualTo(300)
        g.progress.estimatedCompletionTime must beLessThanOrEqualTo(Instant.now)
      }

      "give progress upon resume" in {
        val t = generator(3)
        val g = new AddDocumentsWorkGenerator(t.fileGroup, t.uploads.tail)

        g.progress must beEqualTo(Progress(1, 100L, Instant.MAX))

        g.nextWork
        g.markWorkDone(g.uploads(0))

        g.progress.nFilesProcessed must beEqualTo(2)
        g.progress.nBytesProcessed must beEqualTo(300)
        g.progress.estimatedCompletionTime must beGreaterThanOrEqualTo(Instant.now)
      }

      "report partial progress on an upload without increasing nFilesProcessed" in {
        val g = generator(1)
        g.nextWork
        g.markWorkProgress(g.uploads(0), 0.5 /* 50b */)
        g.progress.nFilesProcessed must beEqualTo(0)
        g.progress.nBytesProcessed must beEqualTo(50)
      }

      "add nBytesProcessed for each worker" in {
        val g = generator(2)
        g.nextWork
        g.nextWork
        g.markWorkProgress(g.uploads(0), 0.5 /* 50b */)
        g.markWorkProgress(g.uploads(1), 0.75 /* 150b */)
        g.progress.nBytesProcessed must beEqualTo(200)
      }

      "treat each upload's nBytesProcessed as absolute" in {
        val g = generator(1)
        g.nextWork
        g.markWorkProgress(g.uploads(0), 0.5 /* 50b */)
        g.markWorkProgress(g.uploads(0), 0.75 /* 75b */ )
        g.progress.nBytesProcessed must beEqualTo(75)
      }

      "un-count partial progress when an upload is done" in {
        val g = generator(2)
        g.nextWork
        g.markWorkProgress(g.uploads(0), 0.5)
        g.markWorkDone(g.uploads(0))
        g.progress.nBytesProcessed must beEqualTo(100)
      }
    }
  }
}
