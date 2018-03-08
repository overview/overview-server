//package com.overviewdocs.ingest.convert
//
//import java.time.Instant
//import java.util.UUID
//
///** Serves GET requests from converters with WrittenFile2 data; handles POST
//  * requests by generating StepOutputFragments.
//  *
//  * Features (and how they're implemented):
//  * * Listens on the specified port -- using akka-http.
//  * * Handles multiple worker types. Requesters pass the type they want using
//  *   `.handle(workerType, ...)`; this server holds a work queue per type and
//  *   so only a worker that requests work of that type will see it.
//  * * Reschedules after worker hangs. (This happens during deploy or crashes.)
//  *   We track the last time we've received a message concerning a file; if it's
//  *   been too long we'll serve the same work to the next worker that asks.
//  * * Returns 404 upon POST to files we aren't converting. This can happen if
//  *   worker A gets disconnected past the timeout, worker B swoops in and
//  *   finishes its work, and then worker A comes back online.
//  * * Serves work to HTTP clients: a multi-part response with JSON parameters
//  *   and blob data.
//  * * Accepts multi-part POSTs from HTTP clients, so an entire conversion can
//  *   happen in a one HTTP POST. (Also accepts multiple HTTP POSTs.)
//  * * Lets workers long-poll when GETting a task.
//  * * Lets workers long-poll to check a task for cancellation. We give each
//  *   task an ID to make this easy.
//  */
//class MinimportHttpServer(
//  val httpPort: Int,
//  val idleTimeout: FiniteDuration
//)(implicit ec: ExecutionContext, mat: Materializer) {
//  case class State(byType: Map[MinimportWorkerType, TypeState])
//
//  case class Worker(
//    workerType: MinimportWorkerType,
//    address: InetAddres
//  )
//
//  case class PendingTask(
//    id: UUID,
//    writtenFile2: WrittenFile2,
//    fragmentSink: FragmentSink
//  )
//
//  case class RunningTask(
//    id: UUID,
//    worker: Worker,
//    writtenFile2: WrittenFile2,
//    cancelSource: Sink[Unit, Source[Unit, akka.NotUsed]],
//    fragmentSink: Sink[StepOutputFragment, akka.NotUsed],
//    lastActivityAt: Instant
//  )
//
//  case class WaitingWorker(
//    worker: Worker
//  )
//
//  case class TypeState(
//    pendingTasks: Vector[PendingTask],
//    runningTasks: Vector[RunningTask],
//    waitingWorkers: Vector[WaitingWorker]
//  )
//
//  private val httpRequestResponseFlow: Flow[HttpRequest, HttpResponse, akka.NotUsed] = {
//
//  }
//
//  private val httpConnectionSink: Sink[Http.IncomingConnection, akka.NotUsed] = {
//    Sink.foreach[Http.IncomingConnection](_.handleWith(httpRequestResponseFlow))
//  }
//}
