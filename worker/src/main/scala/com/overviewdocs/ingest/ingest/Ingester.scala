package com.overviewdocs.ingest.ingest

import akka.stream.scaladsl.{Flow,Source}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.model.{ProcessedFile2,IngestedRootFile2,ResumedFileGroupJob}
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.util.Logger

object Ingester {
  private val logger = Logger.forClass(getClass)

  /** Converts Processed File2s into Ingested ones by creating Documents and
    * DocumentProcessingErrors.
    *
    * Any INGESTED File2 has had all its Documents and/or File2Errors written.
    * Ingesting an INGESTED File2 is a no-op.
    *
    * Ingesting can't be canceled. (Compared to processing, it's super-quick.
    * Users can wait the few seconds.)
    *
    * This method is meant to be called with an infinite input stream, producing
    * an infinite output stream. Input and output orderings are not defined, but
    * this class enforces an _internal_ rule that database writes must happen on
    * children before parents, so a crash doesn't leave ingested parents with
    * non-ingested children.
    */
  def ingest(
    file2Writer: File2Writer,
    batchSize: Int = 500,
    maxBatchWait: FiniteDuration = FiniteDuration(100, "ms")
  )(implicit ec: ExecutionContext): Flow[ProcessedFile2, IngestedRootFile2, akka.NotUsed] = {
    case class State(
      /** "holding": parents whose children aren't written yet.
        *
        * Whenever there are no ongoing conversions or writes, this should be
        * empty. It is designed so that even during busy conversion, it only
        * consumes ~100 bytes per un-ingested parent file.
        *
        * Never mutate this variable. We just use mutable.LongMap for the
        * implementation.
        */
      unwrittenParents: mutable.LongMap[ProcessedFile2],

      /** "unseen": children whose parents haven't arrived yet.
        *
        * If a child arrives before its parent, we'll ingest it right away:
        * why not? When the parent finally arrives, we'll want it to know
        * how many children were written.
        *
        * Never mutate this variable. We just use mutable.LongMap for the
        * implementation.
        */
      nChildrenOfUnseenParents: mutable.LongMap[Int],
    )

    /** From a batch of processed files, returns (state, toIngest).
      *
      * The return `toIngest` can include both parents and their children in the
      * same batch.
      */
    def fixHierarchy(
      state: State,
      file2s: Vector[ProcessedFile2]
    ): (State, Vector[ProcessedFile2]) = {
      // A pool of ProcessedFile2s we have seen previously.
      //
      // invariant: areChildrenIngested == false.
      //
      // These are guaranteed to be different from `file2s`, because Overview
      // doesn't process any file twice.
      val holding = state.unwrittenParents.clone

      // Number of children of each `parent` that are already written.
      //
      // invariant: we haven't seen the parent yet.
      val nChildrenOfUnseenParents = state.nChildrenOfUnseenParents.clone

      // Focus ProcessedFile2s: we need to look at them to learn whether to
      // write or hold them.
      //
      // The initial "staging" is our input `file2s`. We'll loop to change it.
      //
      // Side-effect: we clear entries from "nChildrenOfUnseenParents" when
      // input is a parent, and we update the parent's nIngestedChildren.
      var staging: Vector[ProcessedFile2] = file2s
        .map(file2 => nChildrenOfUnseenParents.remove(file2.id) match {
          case None => file2
          case Some(n) => file2.copy(nIngestedChildren=file2.nIngestedChildren + n)
        })

      // ProcessedFile2s with invariant: areChildrenIngested == true
      //
      // The children may be ingested within this very batch.
      val ingesting: mutable.ArrayBuffer[Vector[ProcessedFile2]] = mutable.ArrayBuffer.empty

      while (staging.nonEmpty) {
        // Move all entries from "staging" to either "ingesting" or "holding".
        val (toIngest, toHold) = staging.partition(_.areChildrenIngested)
        ingesting.+=(toIngest)
        holding.++=(toHold.map(f => (f.id -> f)))

        // Count the children we added to `ingesting` this iteration
        val focusParentCounts: Map[Long,Int] = toIngest
          .flatMap(_.parentId)
          .groupBy(identity)
          .mapValues(_.size) // parentId => nChildrenWrittenThisStep

        // For newly-`ingesting` children: remove parents from "holding", adjust
        // their counts, and store under "staging".
        //
        // For each child we're `ingesting` without a parent, update
        // "nChildrenOfUnseenParents".
        //
        // "staging" will become empty iff "toIngest" only contains roots and
        // children of unseen parents.
        staging = focusParentCounts
          .flatMap((kv: Tuple2[Long,Int]) => holding.remove(kv._1) match {
            case None => {
              // We need to update nChildrenOfUnseenChildren to mark that we 
              // saw a child of an unseen parent. Then we'll return None:
              // it's not possible to stage the parent, because we haven't
              // received it yet.
              val n = nChildrenOfUnseenParents.getOrElse(kv._1, 0) + kv._2
              nChildrenOfUnseenParents.+=(kv._1 -> n)
              None
            }
            case Some(parent) => {
              // Happy path. Update the parent: we'll inspect it next loop.
              Some(parent.copy(nIngestedChildren = parent.nIngestedChildren + kv._2))
            }
          })
          .toVector
      }

      holding.repack
      nChildrenOfUnseenParents.repack
      (State(holding, nChildrenOfUnseenParents), ingesting.flatten.toVector)
    }

    def logStatus(fileGroupJob: ResumedFileGroupJob, file2s: Vector[ProcessedFile2]) {
      val fileGroupId = fileGroupJob.fileGroupId
      val nProcessedThisBatch = file2s.size
      val nRootsThisBatch = file2s.filter(_.parentId.isEmpty).size
      val nRootsTotal = fileGroupJob.fileGroup.nFiles.get

      logger.info(
        "FileGroup {}: batch-ingest {} Files, {} roots (group has {} roots)",
        fileGroupId,
        nProcessedThisBatch,
        nRootsThisBatch,
        nRootsTotal
      )
    }

    def ingestBatch(
      state: State,
      file2s: Vector[ProcessedFile2]
    )(implicit ec: ExecutionContext): Future[Tuple2[State, Vector[IngestedRootFile2]]] = {
      // This is effectively synchronous: it's only called once at a time,
      // always from the same thread, waiting for the returned Future to
      // complete before calling again. (akka-streams ftw.)
      val (nextState, toIngest) = fixHierarchy(state, file2s)

      toIngest
        .groupBy(_.fileGroupJob)
        .foreach((logStatus _).tupled)

      for {
        _ <- if (toIngest.nonEmpty) { file2Writer.ingestBatch(toIngest) } else { Future.unit }
      } yield {
        val roots = toIngest.filter(_.parentId.isEmpty)
        val ingestedRoots = roots.map(root => IngestedRootFile2(root.id, root.fileGroupJob))
        (nextState, ingestedRoots)
      }
    }

    type StateWithOutput = Tuple2[State, Vector[IngestedRootFile2]]
    def scanStep(
      stateWithOutput: StateWithOutput,
      file2s: Vector[ProcessedFile2]
    )(implicit ec: ExecutionContext): Future[Tuple2[State, Vector[IngestedRootFile2]]] = {
      ingestBatch(stateWithOutput._1, file2s)
    }

    val initialState = State(mutable.LongMap.empty, mutable.LongMap.empty)
    val initialStateWithOutput = (initialState, Vector.empty[IngestedRootFile2])

    Flow[ProcessedFile2]
      .groupedWithin(batchSize, maxBatchWait)
      .map(_.toVector)
      .scanAsync(initialStateWithOutput)(scanStep)
      .mapConcat(_._2)
  }
}
