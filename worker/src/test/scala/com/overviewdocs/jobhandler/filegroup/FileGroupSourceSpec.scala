package com.overviewdocs.jobhandler.filegroup

import akka.stream.scaladsl.{Keep,Sink}
import akka.stream.testkit.scaladsl.TestSink
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.models.FileGroup
import com.overviewdocs.test.{ActorSystemContext,DbSpecification}

class FileGroupSourceSpec extends DbSpecification {
  sequential

// [adamhooper, 2018-03-06] I have tried for ages, and I can't convince
// SourceQueueWithComplete or Source.actorRef to work! They keep deadlocking
// during test.

//  trait BaseScope extends DbScope with ActorSystemContext {
//    implicit val ec = system.dispatcher
//
//    override def await[T](future: Future[T]): T = {
//      blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
//    }
//
//    val onProgressCalls = ArrayBuffer.empty[FileGroupProgressState]
//    val onProgress: (FileGroupProgressState => Unit) = onProgressCalls.+= _
//
//    lazy val subject = {
//      System.err.println("Loading subject")
//      new FileGroupSource(database, onProgress)
//    }
//
//    val sink = TestSink.probe[ResumedFileGroupJob]
//
//    // Materialize graph
//    lazy val start = {
//      val source = subject.source
//      source.runWith(sink)
//    }
//
//    lazy val sinkProbe = start
//
//    def allResults: Vector[ResumedFileGroupJob] = {
//      subject.enqueue ! akka.actor.Status.Success(akka.NotUsed)
//      System.err.println("Reading vec")
//      sinkProbe.toStrict(scala.concurrent.duration.FiniteDuration(2, "s")).to[Vector]
//    }
//
//    val documentSet = factory.documentSet()
//
//    def createFileGroup(addToDocumentSetId: Option[Long], deleted: Boolean) = factory.fileGroup(
//      addToDocumentSetId=addToDocumentSetId,
//      deleted=deleted,
//      ocr=Some(false),
//      splitDocuments=Some(false),
//      lang=Some("en"),
//      nFiles=Some(2),
//      nBytes=Some(200),
//      nFilesProcessed=Some(1),
//      nBytesProcessed=Some(100)
//    )
//  }
//
//  "FileGroupSource" should {
//    "resume a FileGroup via database read" in new BaseScope {
//      val group1 = createFileGroup(Some(documentSet.id), false)
//      start
//      allResults.map(_.fileGroup) must beEqualTo(Vector(group1))
//    }
//
//    "not resume deleted or not-submitted FileGroups" in new BaseScope {
//      createFileGroup(Some(documentSet.id), true)
//      factory.fileGroup(addToDocumentSetId=None)
//      start
//      allResults.map(_.fileGroup) must beEqualTo(Vector())
//    }
//
//    "enqueue a FileGroup via ActorRef" in new BaseScope {
//      val group1 = createFileGroup(Some(documentSet.id), false)
//      start
//      val group2 = podoFactory.fileGroup()
//      subject.enqueue ! group2
//      allResults.map(_.fileGroup) must beEqualTo(Vector(group1, group2)) // first come resumes, next enqueues
//    }
//
//    "set up progress reporting" in new BaseScope {
//      val group1 = createFileGroup(Some(documentSet.id), false)
//      start
//      val job = allResults(0)
//      job.progressState.fileGroup must beEqualTo(group1)
//      job.progressState.incrementNFilesIngested
//      job.progressState.setBytesProcessed(0L, 10L)
//      onProgressCalls must beEqualTo(Vector(job.progressState, job.progressState))
//
//      // A bit of an integration test...
//      val report = job.progressState.getProgressReport
//      report.nFilesProcessed must beEqualTo(1)
//      report.nBytesProcessed must beEqualTo(10L)
//    }
//
//    // TODO test resume counts ingested files+bytes
//  }
}
