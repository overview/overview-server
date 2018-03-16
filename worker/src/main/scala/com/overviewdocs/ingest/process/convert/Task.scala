package com.overviewdocs.ingest.process.convert

import akka.stream.scaladsl.Sink

import com.overviewdocs.ingest.model.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.ingest.process.StepOutputFragmentCollector

case class Task(
  writtenFile2: WrittenFile2,
  stepOutputFragmentCollector: StepOutputFragmentCollector,
  sink: Sink[ConvertOutputElement, akka.NotUsed]
) {
  def isCanceled: Boolean = writtenFile2.fileGroupJob.isCanceled
}
