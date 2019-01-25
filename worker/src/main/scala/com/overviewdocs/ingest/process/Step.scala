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

trait Step {
  val id: String
  val progressWeight: Double
  val flow: Flow[WrittenFile2, ConvertOutputElement, Route]
}

object Step {
  case class SimpleStep(
    override val id: String,
    override val progressWeight: Double,
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route]
  ) extends Step

  case class ErrorStep(override val id: String, errorMessage: String, file2Writer: File2Writer)(implicit ec: ExecutionContext) extends Step {
    override val progressWeight = 1.0
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route] = {
      Flow.apply[WrittenFile2]
        .mapAsync(1) { writtenFile =>
          for {
            processedFile <- file2Writer.setProcessed(writtenFile, 0, Some(errorMessage))
          } yield {
            writtenFile.progressPiece.report(1.0)
            ConvertOutputElement.ToIngest(processedFile)
          }
        }
        .mapMaterializedValue(_ => Directives.reject)
    }
  }

  case class StepSpec(stepId: String, progressWeight: Double)

  class HttpSteps(
    stepSpecs: Vector[StepSpec],
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
      stepSpecs.map(spec => buildStep(spec, mat.system))
    }

    private def buildStep(spec: StepSpec, actorRefFactory: ActorRefFactory): Step = {
      val fragmentCollector = new StepOutputFragmentCollector(file2Writer, spec.stepId, spec.progressWeight)
      val taskServer = new HttpStepHandler(spec.stepId, file2Writer.blobStorage, fragmentCollector, maxNWorkers, workerIdleTimeout, httpCreateIdleTimeout)
      val flow = taskServer.flow(actorRefFactory)
      SimpleStep(spec.stepId, spec.progressWeight, taskServer.flow(actorRefFactory))
    }
  }

  def all(
    file2Writer: File2Writer,
    maxNHttpWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  )(implicit mat: ActorMaterializer): Vector[Step] = {
    new HttpSteps(
      Vector(
        StepSpec("Archive", 0.1),
        StepSpec("Email", 0.1),
        StepSpec("Html", 0.75), // 1.0 if !wantSplitByPage, 0.5 otherwise
        StepSpec("Image", 1.0),
        StepSpec("Office", 0.75),
        StepSpec("Pdf", 1.0),
        StepSpec("PdfOcr", 0.75),
        StepSpec("Pst", 0.1),
        StepSpec("Text", 0.75) // 1.0 if !wantSplitByPage, 0.5 otherwise
      ),
      file2Writer,
      maxNHttpWorkers,
      workerIdleTimeout,
      httpCreateIdleTimeout
    ).steps ++ Vector(
      ErrorStep("Unhandled", "unhandled", file2Writer)(mat.executionContext),
      ErrorStep("Canceled", "canceled", file2Writer)(mat.executionContext)
    )
  }
}
