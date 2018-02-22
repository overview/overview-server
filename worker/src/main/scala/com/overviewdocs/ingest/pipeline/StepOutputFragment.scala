package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.JsObject

/** Part of the output of a Step: something we should write somewhere.
  *
  * Step data is sent over the wire: data is serialized. So the order in
  * which we process data is important. This trait class describes that order.
  *
  * This trait does not include Done, Error or Canceled. See StepOutputEnd
  * for those.
  */
sealed trait StepOutputFragment
object StepOutputFragment {
  /** Messages that lead us to write to a File2. */
  sealed trait File2Fragment extends StepOutputFragment

  /** Heartbeat: tells us (and the end-user) the pipeline is alive. */
  sealed trait Progress extends StepOutputFragment {
    /** `tElapsed / tTotal`, where `tTotal` is the predicted amount of time the
      * job will take.
      */
    def fraction: Double
  }
  trait RationalProgress extends Progress {
    val nProcessed: Int
    val nTotal: Int
    override def fraction = 1.0 * nProcessed / nTotal
  }

  /** nProcessed/nTotal bytes of input have been processed.
    *
    * This is preferred over `RationalProgress` because it can give the user a
    * better understanding of how much work remains.
    */
  case class ProgressBytes(
    override val nProcessed: Int,
    override val nTotal: Int
  ) extends RationalProgress

  /** nProcessed/nTotal File2s of output have been produced.
    *
    * This is preferred over `RationalProgress` because it can give the user a
    * better understanding of how much work remains.
    */
  case class ProgressChildren(
    override val nProcessed: Int,
    override val nTotal: Int
  ) extends RationalProgress

  /** fraction has elapsed of the total predicted processing time for the input
    * File2.
    */
  case class ProgressFraction(
    override val fraction: Double
  ) extends Progress

  /** Metadata about a new File2 we should write.
    *
    * This can be used to create a CREATED File2 in the database. The next
    * fragment(s) should stream its blob contents.
    *
    * The first indexInParent in a stream must be 0. Subsequent File2Headers'
    * indexInParent values must be 1, then 2, then 3, and so on.
    *
    * When a File2Header fragment arrives with indexInParent=1, the receiver
    * can assume indexInParent=0's File2 is completely transferred. The previous
    * File2 will transition to WRITTEN, meaning we can leave it alone if we
    * restart the pipeline with the same File2 (during a deploy, say, or after
    * a crash).
    */
  case class File2Header(
    indexInParent: Int,
    filename: String,
    contentType: String,
    metadataJson: JsObject,
    pipelineOptions: JsObject
  ) extends File2Fragment

  /** Blob data for the File2.
    *
    * The indexInParent must match the previous File2Header.
    */
  case class Blob(
    indexInParent: Int,
    rawBytes: Source[ByteString, _]
  ) extends File2Fragment

  /** Indicates there will be no Blob data for the File2.
    *
    * This is for an edge case. Imagine a "OCR" pipeline element that outputs a
    * PDF the same as the input PDF, except with OCR applied. There's no need
    * to pass the blob over the pipeline, because the pipeline already has it.
    *
    * There is no `indexInParent`: this message only makes sense for child `0`.
    */
  case object InheritBlob extends File2Fragment

  /** Text data for the File2.
    *
    * The indexInParent must match the previous File2Header.
    *
    * Text data is optional for all but a leaf File2.
    */
  case class Text(
    indexInParent: Int,
    utf8Bytes: Source[ByteString, _]
  ) extends File2Fragment

  /** Thumbnail data for the File2.
    *
    * The indexInParent must match the previous File2Header.
    *
    * Thumbnail data is optional for all but a leaf File2.
    */
  case class Thumbnail(
    indexInParent: Int,
    contentType: String,
    bytes: Source[ByteString, _]
  ) extends File2Fragment
}
