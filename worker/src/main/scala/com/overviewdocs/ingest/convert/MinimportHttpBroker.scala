package com.overviewdocs.ingest.convert

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.pipeline.{StepOutputFragment,StepOutputEnd}
import com.overviewdocs.models.File2

class MinimportHttpBroker extends MinimportBroker() {
  def requestConvert(
    workerType: MinimportWorkerType,
    file2: File2
  )(implicit ec: ExecutionContext): Source[StepOutputFragment, Future[StepOutputEnd]] = ???
}
