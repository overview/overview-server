package controllers.iteratees // TODO rename package to "streams"

import akka.stream.{Attributes,FlowShape,Inlet,Outlet}
import akka.stream.stage._
import akka.util.ByteString

/** Smooshes and slices input ByteStrings so output ByteStrings are all the same
  * length (except the final ByteString, which may be smaller).
  *
  * This is based on
  * http://doc.akka.io/docs/akka/2.5.1/scala/stream/stream-cookbook.html with
  * one difference: it will wait for more input when the buffer isn't large
  * enough, instead of emitting small chunks.
  */
class Chunker(val chunkSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("Chunker.in")
  val out = Outlet[ByteString]("Chunker.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var buffer = ByteString.empty

    setHandler(out, new OutHandler {
      override def onPull(): Unit = emitChunkOrPull()
    })

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        buffer ++= grab(in)
        emitChunkOrPull()
      }

      override def onUpstreamFinish(): Unit = emitChunkOrPull()
    })

    private def emitChunkOrPull(): Unit = {
      if (isClosed(in) && buffer.isEmpty) {
        // We've transmitted all our input
        completeStage()
      } else if (!isClosed(in) && buffer.size < chunkSize) {
        // We won't push until our buffer is big enough. Pull.
        pull(in)
      } else if (isAvailable(out)) {
        assert(!buffer.isEmpty)
        // Out is waiting: either for a chunkSize-sized ByteString, or for a
        // final smaller-than-chunkSize ByteString if isClosed(in).
        val (chunk, nextBuffer) = buffer.splitAt(chunkSize)
        buffer = nextBuffer
        push(out, chunk)
      } // else we twiddle our thumbs. How did we get here?
    }
  }
}
