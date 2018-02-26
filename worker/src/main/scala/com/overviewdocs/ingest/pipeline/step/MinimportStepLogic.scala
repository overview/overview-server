package com.overviewdocs.ingest.pipeline.step

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.convert.{MinimportBroker, MinimportWorkerType}
import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}

class MinimportStepLogic(broker: MinimportBroker, workerType: MinimportWorkerType) extends StepLogic {
  override def processIntoFragments(
    file2: WrittenFile2,
    canceled: Future[akka.Done]
  )(implicit ec: ExecutionContext): Source[StepOutputFragment, akka.NotUsed] = ???
}
