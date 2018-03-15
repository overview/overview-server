package com.overviewdocs.ingest.convert

import akka.stream.scaladsl.Sink

import com.overviewdocs.ingest.models.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.pipeline.StepOutputFragmentCollector

case class Task(
  writtenFile2: WrittenFile2,
  stepOutputFragmentCollector: StepOutputFragmentCollector,
  sink: Sink[ConvertOutputElement, akka.NotUsed]
) {
  def isCanceled: Boolean = writtenFile2.fileGroupJob.isCanceled
}
