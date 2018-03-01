package com.overviewdocs.ingest.pipeline.step

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.convert.{MinimportBroker, MinimportWorkerType}
import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}

class MinimportStepLogic(broker: MinimportBroker, workerType: MinimportWorkerType) extends StepLogic {
  override def toChildFragments(
    file2: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = ???
}
