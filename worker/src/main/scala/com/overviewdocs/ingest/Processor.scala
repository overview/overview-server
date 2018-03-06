package com.overviewdocs.ingest

import akka.stream.{Graph,FlowShape,Materializer,OverflowStrategy}
import akka.stream.scaladsl.{Flow,GraphDSL,Merge,MergePreferred}
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}
import com.overviewdocs.ingest.pipeline.Step

/** Sends WrittenFile2s to Decider and Steps (recursively) and outputs
  * ProcessedFile2s.
  *
  * There's recursion built in: not only does Processor run a Step on each
  * writtenFile2 input: it also runs a Step on each writtenFile2 output by each
  * Step.
  *
  * Recursion leads to potential deadlock: a StepLogic that outputs lots of
  * WrittenFile2s meant for recursion (e.g., zipfiles full of zipfiles) will
  * eventually receive them again as input: a feedback loop. The solution: the
  * caller should buffer the output. Assume a WrittenFile2 consumes ~200 bytes
  * plus 5kb of metadata. A 10K-element output buffer could consume 50MB and
  * would allow extracting a zipfile full of 10k zipfiles. (Future idea: it
  * would be more scalable to just store WrittenFile2 IDs and read them from the
  * database if there are too many.)
  *
  * The flow:
  *
  *               +---------------------------------------------+
  *               |                                             |
  *               |   +-------+           +--------+            |
  *               |   | merge |           | buffer |            |
  *      in ~>    O~~>o       o<~~~~~~~~~~o        o<~~~~~~~~+  |
  *  WrittenFile2 |   |       |           |        |         |  |
  *               |   +---o---+           +--------+         |  |
  *               |       |                                  |  |
  *               |       |               Steps              |  |
  *               |       |             +-------+            |  |
  *               |       |             | logic |            |  |
  *               |       |             +-------+            |  |
  *               |       v            /         \           |  |
  *               |  +----o-----------o           o-------+  |  |
  *               |  |     decider    | +-------+ | merge o~~+  |
  *               |  |        +       o~| logic |~o-------|     |
  *               |  | stepLogicGraph | +-------+ | merge o~~~~>O    ~> out1
  *               |  +----------------o           o-------+     | ProcessedFile2
  *               |                    \         /              |
  *               |                     +-------+               |
  *               |                     | logic |               |
  *               |                     +-------+               |
  *               +---------------------------------------------+
  */
class Processor(steps: Vector[Step], file2Writer: File2Writer, nDeciders: Int, recurseBufferSize: Int) {
  def graph(implicit ec: ExecutionContext, mat: Materializer): Graph[FlowShape[WrittenFile2, ProcessedFile2], akka.NotUsed] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merger = builder.add(MergePreferred[WrittenFile2](1))

      // 1..nSteps splitter
      val decider = builder.add(new Decider(steps, file2Writer.blobStorage, nDeciders).graph)

      // Vector, nSteps long, of 1..1 FanOutShape2s.
      val stepGraphs = steps.map(step => builder.add(step.toGraph(file2Writer)))

      val mergeOutputWrittenFiles = builder.add(Merge[WrittenFile2](Step.All.length))
      val mergeOutputProcessedFiles = builder.add(Merge[ProcessedFile2](Step.All.length))
      val recurseBuffer = builder.add(Flow.apply[WrittenFile2].buffer(recurseBufferSize, OverflowStrategy.fail))

      merger ~> decider
      steps.zipWithIndex.foreach { case (_, i) =>
                decider ~> stepGraphs(i).in
                           stepGraphs(i).out0 ~> mergeOutputWrittenFiles
                           stepGraphs(i).out1 ~> mergeOutputProcessedFiles
      }
      merger.in(0) <~ recurseBuffer <~ mergeOutputWrittenFiles // highest-priority merger input

      FlowShape(merger.in(1), mergeOutputProcessedFiles.out) // Flow input is lower-priority merger input
    }
  }
}
