package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}

/** Processes a File2, returning itself and its derived files.
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
  * _input_ File2 (as PROCESSED), its children (as PROCESSED), its grandchildren
  * (as PROCESSED), and so on. **Ordering is undefined.**
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
    * file2) will be emitted PROCESSED, in any order.
    *
    * Be carfeful when ingesting. A parent can only be INGESTED once its
    * children are INGESTED.
    */
  def process(file2: WrittenFile2)(implicit ec: ExecutionContext): Source[ProcessedFile2, akka.NotUsed]
}
