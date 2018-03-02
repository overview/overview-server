package com.overviewdocs.ingest

import akka.stream.{FlowShape,Graph,Materializer,OverflowStrategy}
import akka.stream.scaladsl.{Flow,GraphDSL,Merge,MergePreferred}
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.pipeline.Step
import com.overviewdocs.ingest.models.{WrittenFile2, ProcessedFile2, IngestedRootFile2}

class SystemGraph(file2Writer: File2Writer, nDeciders: Int, recurseBufferSize: Int) {
  def graph(implicit ec: ExecutionContext, mat: Materializer): Graph[FlowShape[WrittenFile2, IngestedRootFile2], akka.NotUsed] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source = builder.add(Flow.apply[WrittenFile2])

      val toDecide = builder.add(MergePreferred[WrittenFile2](1))

      // 1..nSteps splitter
      val decider = builder.add(new Decider(file2Writer.blobStorage, nDeciders).graph)

      // Vector, nSteps long, of 1..1 FanOutShape2s.
      val process = Step.All.map(step => builder.add(step.toGraph(file2Writer)))

      val processOutputToDecide = builder.add(Merge[WrittenFile2](Step.All.length))
      val processOutputToIngest = builder.add(Merge[ProcessedFile2](Step.All.length))
      val processOutputBuffer = builder.add(Flow.apply[WrittenFile2].buffer(recurseBufferSize, OverflowStrategy.fail))

      val ingest = builder.add(Ingester.ingest(file2Writer))

      source ~> toDecide.in(1)
                toDecide       ~> decider

      Step.All.zipWithIndex.foreach { case (_, i) =>
                                  decider.out(i) ~> process(i).in
                                                    process(i).out0 ~> processOutputToDecide
                                                    process(i).out1 ~> processOutputToIngest
      }
                toDecide.in(0)     <~ processOutputBuffer    <~ processOutputToDecide
                                                                processOutputToIngest ~> ingest


      FlowShape(source.in, ingest.out)
    }
  }
}
