package com.overviewdocs.jobhandler.filegroup

import akka.stream.scaladsl.Sink
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.models.FileGroup
import com.overviewdocs.test.{ActorSystemContext,DbSpecification}

class FileGroupSourceSpec extends DbSpecification {
  sequential

  trait BaseScope extends DbScope with ActorSystemContext {
    implicit val ec = system.dispatcher

    override def await[T](future: Future[T]): T = {
      blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
    }

    lazy val subject = new FileGroupSource(database)
    val sink = Sink.seq[FileGroup].async

    // Materialize graph
    lazy val start = subject.source.async.runWith(sink)

    def allResults: Vector[FileGroup] = {
      subject.enqueue ! akka.actor.Status.Success(akka.NotUsed)
      await(start).to[Vector]
    }

    val documentSet = factory.documentSet()

    def createFileGroup(addToDocumentSetId: Option[Long], deleted: Boolean) = factory.fileGroup(
      addToDocumentSetId=addToDocumentSetId,
      deleted=deleted,
      ocr=Some(false),
      splitDocuments=Some(false),
      lang=Some("en"),
      nFiles=Some(2),
      nBytes=Some(200),
      nFilesProcessed=Some(1),
      nBytesProcessed=Some(100)
    )
  }

  "FileGroupSource" should {
    "resume a FileGroup via database read" in new BaseScope {
      val group1 = createFileGroup(Some(documentSet.id), false)
      start
      allResults must beEqualTo(Vector(group1))
    }

    "not resume deleted or not-submitted FileGroups" in new BaseScope {
      createFileGroup(Some(documentSet.id), true)
      factory.fileGroup(addToDocumentSetId=None)
      start
      allResults must beEqualTo(Vector())
    }

    "enqueue a FileGroup via ActorRef" in new BaseScope {
      val group1 = createFileGroup(Some(documentSet.id), false)
      start
      val group2 = podoFactory.fileGroup()
      subject.enqueue ! group2
      allResults must beEqualTo(Vector(group1, group2)) // first come resumes, next enqueues
    }
  }
}
