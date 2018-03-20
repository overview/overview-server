package com.overviewdocs.ingest.process

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{ConvertOutputElement,WrittenFile2}
import com.overviewdocs.util.Logger

/** Runs StepLogic on a stream of WrittenFile2s.
  *
  * This produces WrittenFile2s to pass to the next pipeline step and
  * ProcessedFile2s to pass to the ingester.
  *
  * `writtenFile2.onProgress()` will be called every time the StepLogic outputs
  * Progress fragments. `writtenFile2.canceled` should be handled by the
  * StepLogic.
  *
  * This Flow converts and outputs its _inputs_, too. Each input element will
  * transition from WRITTEN to PROCESSED (while being written to the database),
  * and it will be emitted. It will have `nChildren` and may have a
  * `processingError`. Even after cancellation, the StepLogic will complete; in
  * that case, `processingError` will become `"canceled"`.
  *
  *               +-----------------------------------+
  *               |         StepLogic substreams      |
  *               |              +-------+            |
  *               |              | logic |            |
  *               |              +-------+            |
  *               |             /         \           |
  *               |  +---------o           o-------+  |
  *               |  | process | +-------+ | merge |  |
  *     in  ~>    O~~o         o~| logic |~o       o~~O      ~> out
  * WrittenFile2  |  |         | +-------+ |       |  | ConvertOutputElement
  *               |  +---------o           o-------+  |
  *               |             \         /           |
  *               |              +-------+            |
  *               |              | logic |            |
  *               |              +-------+            |
  *               +-----------------------------------+
  *
  * `Parallelism` is the number of simultaneous conversions. If the logic is
  * in-process, you should set `parallelism` low: nCPUs, perhaps. If the logic
  * is a broker+worker system, set `parallelism` as high as the maximum expected
  * number of workers: otherwise, the broker will not be able to queue enough
  * work for the workers.
  *
  * There is a potential deadlock in buffering: a StepLogic that outputs lots of
  * WrittenFile2s meant for recursion (e.g., zipfiles full of zipfiles) should
  * eventually receive them again as input: a feedback loop. The solution: the
  * caller should buffer the output. Assume a WrittenFile2 consumes ~200 bytes
  * plus 5kb of metadata. A 10K-element output buffer could consume 50MB and
  * would allow extracting a zipfile full of 10k zipfiles. (Future idea: it
  * would be more scalable to buffer only the WrittenFile2 IDs, rebuilding the
  * WrittenFile2s from the database, if the buffer gets very full.)
  */
class StepLogicFlow(logic: StepLogic, file2Writer: File2Writer, parallelism: Int) {
  private val logger = Logger.forClass(getClass)
  private val stepOutputFragmentCollector = new StepOutputFragmentCollector(file2Writer, logic.getClass.getName)

  def flow(implicit mat: Materializer): Flow[WrittenFile2, ConvertOutputElement, akka.NotUsed] = {
    Flow.apply[WrittenFile2]
      .flatMapMerge(parallelism, singleFileSource _)
  }

  private def singleFileSource(
    parentFile2: WrittenFile2
  )(implicit mat: Materializer): Source[ConvertOutputElement, akka.NotUsed] = {
    implicit val ec = mat.executionContext
    logger.info("Processing file2 {} ({}, {} bytes", parentFile2.id, parentFile2.filename, parentFile2.blob.nBytes)

    logic.toChildFragments(file2Writer.blobStorage, parentFile2)
      .via(stepOutputFragmentCollector.flowForParent(parentFile2))
  }
}
