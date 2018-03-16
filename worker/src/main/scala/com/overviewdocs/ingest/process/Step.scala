package com.overviewdocs.ingest.process

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives,RequestContext,Route,RouteResult}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Keep,MergeHub,Sink}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{ConvertOutputElement,WrittenFile2,StepOutputFragment}
import com.overviewdocs.ingest.process.convert.{HttpTaskHandler,Task}
import com.overviewdocs.ingest.process.logic._

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
      Task(writtenFile2, stepOutputFragmentCollector, outputSink)
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
