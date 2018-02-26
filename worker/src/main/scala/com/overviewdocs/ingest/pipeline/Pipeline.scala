package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}

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
  * The caller supplies a WRITTEN or PROCESSED File2. This class outputs the
  * _input_ File2 (as PROCESSED), followed by its children (as PROCESSED),
  * its grandchildren (as PROCESSED), and so on.
  *
  * In essence: this is a depth-first search, pre-ordered. All outputs are
  * PROCESSED, and they are returned in the order in which tasks are completed.
  * (Child2 and Grandchild1 may come before Child1, if that's the way the
  * schedule works out. But parents always come first.)
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
  * The key: if the input File2 is PROCESSED, we can simply select its outputs
  * from the database. If it's WRITTEN, then we have an option: we can delete
  * all its existing outputs from the database and process it from scratch; or
  * we can select them and resume at the point where writes stopped.
  */
trait Pipeline {
  /** Resumes or starts transitioning the given File2 and all its children and
    * grandchildren from WRITTEN to PROCESSED. All File2s (including the input
    * file2) will be emitted PROCESSED, pre-ordered (parents first).
    *
    * Ingest these outputs in reverse order: not in parallel. A parent can only
    * be marked ingested if its children are ingested.
    */
  def process(file2: WrittenFile2)(implicit ec: ExecutionContext): Source[ProcessedFile2, akka.NotUsed]
}
