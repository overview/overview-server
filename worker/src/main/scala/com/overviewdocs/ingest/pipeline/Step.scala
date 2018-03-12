package com.overviewdocs.ingest.pipeline

import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.convert.HttpConverter
import com.overviewdocs.ingest.models.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.pipeline.logic._

sealed trait Step {
  val id: String

  def toFlow(
    file2Writer: File2Writer
  )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed]
}
sealed trait StepLogicStep extends Step {
  val logic: StepLogic
  val parallelism: Int

  override def toFlow(
    file2Writer: File2Writer
  )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = {
    new StepLogicFlow(logic, file2Writer, parallelism).flow
  }
}
object Step {
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

  case class HttpConverterStep(val httpConverter: HttpConverter, override val id: String) extends Step {
    override def toFlow(
      file2Writer: File2Writer
    )(implicit ec: ExecutionContext, mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = {
      httpConverter.createFlow(new StepOutputFragmentCollector(file2Writer, id))
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
    HttpConverterStep(HttpConverter.singleton, "Zip"),
    Ocr,
    SplitExtract,
    Office,
    Unhandled
  )
}
