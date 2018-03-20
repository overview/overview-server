package com.overviewdocs.ingest.process

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives,RequestContext,Route,RouteResult}
import akka.stream.{ActorMaterializer,Materializer}
import akka.stream.scaladsl.{Flow,Keep,MergeHub,Sink}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.process.logic._

trait Step {
  val id: String
  val progressWeight: Double
  val flow: Flow[WrittenFile2, ConvertOutputElement, Route]

  // We treat some Steps specially, based on their IDs:
  // "Ocr": skip this Step if the user didn't ask for OCR
  val isOcr: Boolean = (id == "Ocr")
}

object Step {
  case class SimpleStep(
    override val id: String,
    override val progressWeight: Double,
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route]
  ) extends Step

  class StepLogicStep(
    file2Writer: File2Writer,
    logic: StepLogic,
    paralellism: Int
  )(implicit mat: Materializer) extends Step {
    override val id = logic.id
    override val progressWeight = logic.progressWeight
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route] = {
      new StepLogicFlow(logic, file2Writer, paralellism)
        .flow
        .mapMaterializedValue(_ => Directives.reject)
    }
  }

  class HttpSteps(
    stepSpecs: Vector[(String,Double)],
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
    def steps(implicit mat: ActorMaterializer): Vector[Step] = {
      stepSpecs.map(spec => buildStep(spec._1, spec._2, mat.system))
    }

    private def buildStep(stepId: String, progressWeight: Double, actorRefFactory: ActorRefFactory): Step = {
      val fragmentCollector = new StepOutputFragmentCollector(file2Writer, stepId, progressWeight)
      val taskServer = new HttpStepHandler(stepId, file2Writer.blobStorage, fragmentCollector, maxNWorkers, workerIdleTimeout, httpCreateIdleTimeout)
      SimpleStep(stepId, progressWeight, taskServer.flow(actorRefFactory))
    }
  }

  def all(
    file2Writer: File2Writer,
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  )(implicit mat: ActorMaterializer): Vector[Step] = Vector(
    new StepLogicStep(file2Writer, new SplitExtractStepLogic, maxNWorkers),
    new StepLogicStep(file2Writer, new OcrStepLogic, maxNWorkers),
    new StepLogicStep(file2Writer, new OfficeStepLogic, maxNWorkers),
    new StepLogicStep(file2Writer, new UnhandledStepLogic, 1),
  ) ++ new HttpSteps(
    Vector(
      "Archive" -> 0.1
    ),
    file2Writer,
    maxNWorkers,
    workerIdleTimeout,
    httpCreateIdleTimeout
  ).steps
}
