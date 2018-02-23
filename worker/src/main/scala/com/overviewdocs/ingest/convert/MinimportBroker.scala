package com.overviewdocs.ingest.convert

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.pipeline.StepOutputFragment
import com.overviewdocs.models.File2

abstract class MinimportBroker {
  val tasks: Map[MinimportWorkerType, Source[MinimportTask, akka.NotUsed]] = ???

  def requestConvert(
    workerType: MinimportWorkerType,
    file2: File2
  )(implicit ec: ExecutionContext): Source[StepOutputFragment, akka.NotUsed]
}
