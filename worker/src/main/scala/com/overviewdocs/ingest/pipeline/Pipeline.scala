package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.models.File2

/** Processes a File2.
  *
  * A pipeline is built from reusable steps. For instance: converting ".doc"
  * might mean 1. run LibreOffice to produce a ".pdf"; and 2. Extract thumbnail.
  * Step 2 is reusable: other pipelines may use it. (A more-optimized ".doc"
  * pipeline would use a single step; but monolith steps are more costly to
  * produce and maintain.)
  *
  * There are two important perspectives to consider:
  *
  * == Caller ==
  *
  * The caller supplies a WRITTEN or PROCESSED File2. This class outputs
  * _leaf_ File2s (which are PROCESSED), _parent_ File2s (which are
  * PROCESSED, always come after all their children, and may have a
  * `processingError`), and finally, the _input_ File2 (which is PROCESSED and
  * may have a `processingError`).
  *
  * In essence: this is a depth-first search.
  *
  * The caller will want to finally "ingest" these outputs: create Documents
  * and File2Errors from them. This is where Leaf and Parent outputs differ:
  * the Leaf should become a Document; the Parent should only be checked
  * for error. (Both should become INGESTED afterwards.)
  *
  * == Inner Workings ==
  *
  * File2 is a state machine built to handle abrupt shutdown or restart. It
  * allows conversions to happen in parallel, and it can resume work after a
  * crash.
  *
  * The key: if a File2 is PROCESSED, we can simply select its outputs from the
  * database. If it's WRITTEN, then we have an option: we can delete all its
  * existing outputs from the database and process it from scratch; or we can
  * select them and resume at the point where writes stopped.
  */
trait Pipeline {
  /** Resumes or commences converting the given file2 and all its children and
    * grandchildren from WRITTEN to PROCESSED. All File2s (including the input
    * file2) will become PROCESSED, pre-ordered (parents first). All File2s
    * (including the input file2) will be emitted, post-ordered (parents last).
    *
    * Ingest these outputs in order: not in parallel. A parent can only be
    * marked ingested if its children are ingested.
    *
    * Ignores any INGESTED File2s. They will not be emitted.
    */
  def processDepthFirst(file2: File2)(implicit ec: ExecutionContext): Source[Pipeline.Output, akka.NotUsed]
}

object Pipeline {
  sealed trait Output {
    val file2: File2
  }
  object Output {
    /** A PROCESSED File2 that should become a Document.
      *
      * It's possible, through a race, that a Document points to a PROCESSED
      * but not INGESTED File2 (particularly during restart).
      */
    case class Leaf(override val file2: File2) extends Output

    /** A PROCESSED File2 that may have children in the database and may have a
      * `processingError`.
      *
      * The Pipeline will only produce a Parent _after_ producing all its
      * children.
      *
      * It's possible, through a race, that a File2Error points to a PROCESSED
      * but not INGESTED File2 (particularly during restart).
      */
    case class Parent(override val file2: File2) extends Output
  }
}
