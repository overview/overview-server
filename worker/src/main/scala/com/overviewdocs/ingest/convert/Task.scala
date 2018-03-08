package com.overviewdocs.ingest.convert

import akka.stream.scaladsl.Sink

import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.StepOutputFragment

case class Task(
  writtenFile2: WrittenFile2,
  sink: Sink[StepOutputFragment, akka.NotUsed]
) {
  def isCanceled: Boolean = writtenFile2.fileGroupJob.isCanceled
}
