package com.overviewdocs.ingest.pipeline.step

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.convert.{MinimportBroker, MinimportWorkerType}
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment,StepOutputEnd}
import com.overviewdocs.models.File2

class MinimportStepLogic(broker: MinimportBroker, workerType: MinimportWorkerType) extends StepLogic {
  override def processIntoFragments(file2: File2)(implicit ec: ExecutionContext): Source[StepOutputFragment, Future[StepOutputEnd]] = ???
}
