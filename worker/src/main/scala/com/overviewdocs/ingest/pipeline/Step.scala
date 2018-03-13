package com.overviewdocs.ingest.pipeline

import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Keep,MergeHub,Sink}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.convert.{HttpTaskServer,Task}
import com.overviewdocs.ingest.models.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.pipeline.logic._

sealed trait Step {
  def canHandle(id: String): Boolean

  def toFlow(
    file2Writer: File2Writer
  )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed]
}
object Step {
  sealed trait StepLogicStep extends Step {
    val id: String
    val logic: StepLogic
    val parallelism: Int

    override def canHandle(anId: String) = anId == id

    override def toFlow(
      file2Writer: File2Writer
    )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = {
      new StepLogicFlow(logic, file2Writer, parallelism).flow
    }
  }

  case object Ocr extends StepLogicStep {
    override val id = "Ocr"
    override val logic = new OcrStepLogic
    override val parallelism = 2
  }

  case object SplitExtract extends StepLogicStep {
    override val id = "SplitExtract"
    override val logic = new SplitExtractStepLogic
    override val parallelism = 2 // TODO correct number of workers
  }

  case object Office extends StepLogicStep {
    override val id = "Office"
    override val logic = new OfficeStepLogic
    override val parallelism = 2
  }

  case class HttpConverterStep(
    stepIds: Vector[String],
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    httpCreateIdleTimeout: FiniteDuration
  ) extends Step {
    override def canHandle(id: String) = stepIds.contains(id)

    private def stepFlow(
      stepId: String,
      file2Writer: File2Writer,
    )(implicit mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, Route] = {
      val fragmentCollector = new StepOutputFragmentCollector(file2Writer, stepId)
      val taskServer = new HttpTaskServer(stepId, maxNWorkers, workerIdleTimeout, httpCreateIdleTimeout)
      val (outputSink, outputSource) = MergeHub.source[ConvertOutputElement].preMaterialize

      val inputSink = Flow.apply[WrittenFile2]
        .map(w => createTask(fragmentCollector, w, outputSink))
        .toMat(taskServer.taskSink)(Keep.right)

      Flow.fromSinkAndSourceCoupledMat(inputSink, outputSource)(Keep.left)
    }

    override def toFlow(
      file2Writer: File2Writer
    )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = ???

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
        .via(stepOutputFragmentCollector.forParent(writtenFile2))
        .to(outputSink)
        .run

      Task(writtenFile2, fragmentSink)
    }
  }

//  case object Zip extends Step {
//    override val id = "Zip"
//    override val logic = ZipStepLogic
//  }
//
//  case object Pst extends Step {
//    override val id = "Pst"
//    override val logic = PstStepLogic
//  }
//
//  case object Image extends Step {
//    override val id = "Image"
//    override val logic = ImageStepLogic
//  }

  case object Unhandled extends StepLogicStep {
    override val id = "Unhandled"
    override val logic = new UnhandledStepLogic
    override val parallelism = 1 // it's super-fast
  }

  val All: Vector[Step] = Vector(
    //HttpConverterStep(Vector("Zip")),
    Ocr,
    SplitExtract,
    Office,
    Unhandled
  )
}
