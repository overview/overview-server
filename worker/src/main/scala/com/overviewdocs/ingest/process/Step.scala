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

  case class UnhandledStep(file2Writer: File2Writer)(implicit ec: ExecutionContext) extends Step {
    override val id = "Unhandled"
    override val progressWeight = 1.0
    override val flow: Flow[WrittenFile2, ConvertOutputElement, Route] = {
      Flow.apply[WrittenFile2]
        .mapAsync(1) { writtenFile =>
          for {
            processedFile <- file2Writer.setProcessed(writtenFile, 0, Some("unhandled"))
          } yield {
            writtenFile.progressPiece.report(1.0)
            ConvertOutputElement.ToIngest(processedFile)
          }
        }
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
    maxNHttpWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  )(implicit mat: ActorMaterializer): Vector[Step] = {
    new HttpSteps(
      Vector(
        "Archive" -> 0.1,
        "Image" -> 1.0,
        "Office" -> 0.75,
        "Pdf" -> 1.0,
        "PdfOcr" -> 0.75
      ),
      file2Writer,
      maxNHttpWorkers,
      workerIdleTimeout,
      httpCreateIdleTimeout
    ).steps ++ Vector(UnhandledStep(file2Writer)(mat.executionContext))
  }
}
