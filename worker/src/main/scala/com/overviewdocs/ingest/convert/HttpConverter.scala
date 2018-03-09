package com.overviewdocs.ingest.convert

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,GraphDSL,MergeHub,Sink}
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.pipeline.{StepOutputFragment,StepOutputFragmentCollector}

/** Creates Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] flows using
  * HTTP-client workers.
  *
  * Each WrittenFile2 input becomes a Task, with a StepOutputFragment sink.
  * Tasks are matched to Worker requests in the WorkerTaskPool, which handles
  * task-related HTTP requests for workers. The HTTP request data gets streamed
  * through the Task's StepOutputFragment sink, which is wired up to the whole
  * Flow's output. The Task flows into a Sink.ignore.
  */
class HttpConverter {
  def createFlow(
    stepOutputFragmentCollector: StepOutputFragmentCollector
  )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = {
    val httpConvertSink = new HttpConvertSink
    val (outputSink, outputSource) = MergeHub.source[ConvertOutputElement].preMaterialize

    val inputSink = Flow.apply[WrittenFile2]
      .map(w => createTask(stepOutputFragmentCollector, w, outputSink))
      .to(httpConvertSink.taskSink)

    Flow.fromSinkAndSourceCoupled(inputSink, outputSource)
  }

  private def createTask(
    stepOutputFragmentCollector: StepOutputFragmentCollector,
    writtenFile2: WrittenFile2,
    outputSink: Sink[ConvertOutputElement, akka.NotUsed]
  )(implicit ec: ExecutionContext, mat: Materializer): Task = {
    val fragmentSink: Sink[StepOutputFragment, akka.NotUsed] = MergeHub.source[StepOutputFragment]
      .via(stepOutputFragmentCollector.forParent(writtenFile2))
      .to(outputSink)
      .run

    Task(writtenFile2, fragmentSink)
  }
}
