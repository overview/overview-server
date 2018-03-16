package com.overviewdocs.ingest.process

import akka.http.scaladsl.server.{Route,RouteConcatenation}
import akka.stream.{Graph,FlowShape,Materializer,OverflowStrategy}
import akka.stream.scaladsl.{Flow,GraphDSL,Merge,MergePreferred,Partition}
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.model.{ConvertOutputElement,WrittenFile2,ProcessedFile2}
import com.overviewdocs.ingest.File2Writer

/** Recurses over a StepProcessor, feeding its WrittenFile2 output elements back
  * to itself so it only outputs ProcessedFile2s.
  *
  * Beware the obvious deadlock. A step that outputs lots of WrittenFile2s
  * (e.g., zipfiles full of zipfiles) will receive those outputs as inputs; but
  * it can't output them until they're consumed, and _it_ is the consumer, busy
  * producing them: a feedback loop. The solution: Processor buffers the output.
  * Assume a WrittenFile2 consumes ~200 bytes plus 5kb of metadata. A
  * 10K-element output buffer could consume 50MB and would allow extracting a
  * zipfile full of 10k zipfiles. (Future idea: avoid keeping WrittenFile2
  * metadata in memory, so we can handle two orders of magnitude more files.)
  *
  * We use MergePreferred instead of Merge, to empty the recursion buffer
  * whenever we can.
  *
  * The flow:
  *
  *                 +---------------------------------------------+
  *                 |   +-------+      +--------+                 |
  *                 |   | merge |      | buffer |  WrittenFile2s  |
  * WrittenFile2 ~> O~~>o       o<~~~~~o        o<~~~~~~+         |
  *                 |   |       |      |        |       |         |
  *                 |   +---o---+      +--------+       |         |
  *                 |       |                           |         |
  *                 |       |   +---------------+   +---o---+     |
  *                 |       |   | StepProcessor |   | split |     |
  *                 |       +~~>o               o~~>o       o~~~~>O ~> ProcessedFile2
  *                 |           |               |   |       |     |
  *                 |           +---------------+   +-------+     |
  *                 +---------------------------------------------+
  *
  * @see [[StepProcessor]], which we recurse over. (It's in the `boilerplate/`
  *      directory.)
  */
class Processor(file2Writer: File2Writer, nDeciders: Int, recurseBufferSize: Int) {
  /** Creates a new [[Flow]] by routing inputs through the appropriate Steps,
    * recursively.
    */
  def flow(
    steps: Vector[Step]
  )(implicit mat: Materializer): Flow[WrittenFile2, ProcessedFile2, Route] = {
    val stepProcessorBuilder = new StepProcessor(file2Writer, nDeciders)

    Flow.fromGraph(GraphDSL.create(stepProcessorBuilder.flow(steps)) { implicit builder => stepProcessor =>
      import GraphDSL.Implicits._

      val merge = builder.add(MergePreferred[WrittenFile2](1))
      val toIngest = builder.add(Flow.apply[ConvertOutputElement].collect[ProcessedFile2] { case ConvertOutputElement.ToIngest(x) => x })
      val toProcess = builder.add(Flow.apply[ConvertOutputElement].collect[WrittenFile2] { case ConvertOutputElement.ToProcess(x) => x })
      val splitOutput = builder.add(Partition[ConvertOutputElement](2, _ match {
        case ConvertOutputElement.ToIngest(_) => 0
        case _ => 1
      }))
      val recurseBuffer = builder.add(Flow.apply[WrittenFile2].buffer(recurseBufferSize, OverflowStrategy.fail))

      merge ~> stepProcessor ~> splitOutput ~> toIngest
                                splitOutput ~> toProcess
      merge.preferred  <~  recurseBuffer  <~   toProcess // highest-priority

      FlowShape(merge.in(0), toIngest.out)
    })
  }
}
