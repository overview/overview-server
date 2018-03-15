package com.overviewdocs.ingest.pipeline

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives,RequestContext,Route,RouteResult}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Keep,MergeHub,Sink}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.convert.{HttpTaskHandler,Task}
import com.overviewdocs.ingest.models.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.pipeline.logic._

trait Step {
  val id: String
  val flow: Flow[WrittenFile2, ConvertOutputElement, Route]

  // We treat some Steps specially, based on their IDs:
  // "Ocr": skip this Step if the user didn't ask for OCR
  val isOcr: Boolean = (id == "Ocr")
}

object Step {
  case class SimpleStep(
    override val id: String,
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route]
  ) extends Step

  class StepLogicStep(
    override val id: String,
    file2Writer: File2Writer,
    logic: StepLogic,
    paralellism: Int
  )(implicit mat: Materializer) extends Step {
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route] = {
      new StepLogicFlow(logic, file2Writer, paralellism)
        .flow
        .mapMaterializedValue(_ => Directives.reject)
    }
  }

  class HttpConverter(
    stepIds: Vector[String],
    file2Writer: File2Writer,
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  ) {
    /** Builds all Steps that have HTTP server components.
      *
      * TODO avoid passing a Materializer here. We end up starting actors before
      * materialization, which is wrong.
      */
    def steps(implicit mat: Materializer): Vector[Step] = stepIds.map(buildStep _)

    private def buildStep(stepId: String)(implicit mat: Materializer): Step = {
      val fragmentCollector = new StepOutputFragmentCollector(file2Writer, stepId)
      val taskServer = new HttpTaskHandler(stepId, maxNWorkers, workerIdleTimeout, httpCreateIdleTimeout)
      val (outputSink, outputSource) = MergeHub.source[ConvertOutputElement].preMaterialize

      val inputSink = Flow.apply[WrittenFile2]
        .map(w => createTask(fragmentCollector, w, outputSink))
        .toMat(taskServer.taskSink(file2Writer.blobStorage))(Keep.right)

      val flow = Flow.fromSinkAndSourceCoupledMat(inputSink, outputSource)(Keep.left)
      SimpleStep(stepId, flow)
    }

    private def createTask(
      stepOutputFragmentCollector: StepOutputFragmentCollector,
      writtenFile2: WrittenFile2,
      outputSink: Sink[ConvertOutputElement, akka.NotUsed]
    )(implicit mat: Materializer): Task = {
      val fragmentSink: Sink[StepOutputFragment, akka.NotUsed] = MergeHub.source[StepOutputFragment]
        // Task ends once we receive an EndFragment
        // TODO consider whether this is the best place to end the stream. (We
        // need to end it _somewhere_, but what's most intuitive?) Also, analyze
        // whether there's any way to "leak" a task, which would make it run
        // forever.
        .takeWhile(fragment => !(fragment.isInstanceOf[StepOutputFragment.EndFragment]), inclusive=true)
        .via(stepOutputFragmentCollector.flowForParent(writtenFile2))
        .to(outputSink)
        .run

      Task(writtenFile2, fragmentSink)
    }
  }

  def all(
    file2Writer: File2Writer,
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  )(implicit mat: Materializer): Vector[Step] = Vector(
    new StepLogicStep("SplitExtract", file2Writer, new SplitExtractStepLogic, maxNWorkers),
    new StepLogicStep("Ocr", file2Writer, new OcrStepLogic, maxNWorkers),
    new StepLogicStep("Office", file2Writer, new OfficeStepLogic, maxNWorkers),
    new StepLogicStep("Unhandled", file2Writer, new UnhandledStepLogic, 1),
  ) ++ new HttpConverter(Vector("Archive"), file2Writer, maxNWorkers, workerIdleTimeout, httpCreateIdleTimeout).steps
}
