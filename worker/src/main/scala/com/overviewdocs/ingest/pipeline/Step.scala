package com.overviewdocs.ingest.pipeline

import akka.stream.{FanOutShape2,Graph,Materializer}
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}
//import com.overviewdocs.ingest.pipeline.logic._

sealed trait Step {
  val id: String
  val logic: StepLogic
  val parallelism: Int

  def toGraph(
    file2Writer: File2Writer
  )(implicit ec: ExecutionContext, mat: Materializer): Graph[FanOutShape2[WrittenFile2, WrittenFile2, ProcessedFile2], akka.NotUsed] = {
    new StepLogicGraph(logic, file2Writer, parallelism).graph
  }
}
object Step {
//  case object Ocr extends Step {
//    override val id = "Ocr"
//    override val logic = OcrStepLogic
//  }
//
//  case object SplitExtract extends Step {
//    override val id = "SplitExtract"
//    override val logic = SplitExtractStepLogic
//    override val parallelism = 2 // TODO correct number of workers
//  }
//
//  case object Office extends Step {
//    override val id = "Office"
//    override val logic = OfficeStepLogic
//  }
//
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
}
