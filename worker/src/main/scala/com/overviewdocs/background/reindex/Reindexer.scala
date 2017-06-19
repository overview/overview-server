package com.overviewdocs.background.reindex

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink,Source}
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.reactivestreams.Publisher
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.duration.FiniteDuration

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{Document,DocumentSetReindexJob}
import com.overviewdocs.models.tables.{Documents,DocumentSetReindexJobs}
import com.overviewdocs.searchindex.LuceneIndexClient

/** Finds and runs [[ReindexJob]]s.
  */
trait Reindexer {
  def clearRunningJobs(implicit ec: ExecutionContext): Future[Unit]
  def nextJob(implicit ec: ExecutionContext): Future[Option[DocumentSetReindexJob]]
  def runJob(job: DocumentSetReindexJob)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit]
}

class DbLuceneReindexer(
  indexClient: LuceneIndexClient,
  nBufferBytes: Long = 1024 * 1024 * 50,
  nDocumentsPerDbFetch: Int = 300,
  writeInterval: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
) extends Reindexer with HasDatabase {
  import database.api._

  private lazy val nextJobCompiled = Compiled {
    DocumentSetReindexJobs
      .filter(_.startedAt.isEmpty)
      .sortBy(_.lastRequestedAt.desc)
  }

  private lazy val jobCompiled = Compiled { id: Rep[Long] =>
    DocumentSetReindexJobs
      .filter(_.id === id)
  }

  private lazy val documentsCompiled = Compiled { documentSetId: Rep[Long] =>
    Documents
      .filter(_.documentSetId === documentSetId)
  }

  private lazy val clearRunningJobsCompiled = Compiled {
    DocumentSetReindexJobs
      .map(_.startedAt)
  }

  private lazy val markRunningJobCompiled = Compiled { id: Rep[Long] =>
    DocumentSetReindexJobs
      .filter(_.id === id)
      .map(row => (row.startedAt, row.progress))
  }

  override def clearRunningJobs(implicit ec: ExecutionContext) = {
    val update = clearRunningJobsCompiled.update(None)
    database.runUnit(update)
  }

  override def nextJob(implicit ec: ExecutionContext) = database.option(nextJobCompiled)

  override def runJob(job: DocumentSetReindexJob)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] = {
    for {
      _ <- markRunningJob(job.id)
      _ <- indexClient.removeDocumentSet(job.documentSetId)
      _ <- indexClient.addDocumentSet(job.documentSetId)
      _ <- streamDocumentsFromDatabaseToIndex(job.documentSetId)
      _ <- indexClient.refresh(job.documentSetId)
      _ <- deleteJob(job.id)
    } yield ()
  }

  private def markRunningJob(id: Long): Future[Unit] = {
    val update = markRunningJobCompiled(id).update(Some(Instant.now), 0.0)
    database.runUnit(update)
  }

  private def deleteJob(id: Long): Future[Unit] = {
    database.delete(jobCompiled(id))
  }

  private def streamDocumentsFromDatabaseToIndex(documentSetId: Long)(implicit mat: Materializer): Future[Unit] = {
    val source: Source[immutable.Seq[Document], _] = getDocumentsSource(documentSetId)
    val sink: Sink[immutable.Seq[Document], Future[Unit]] = getDocumentsSink(documentSetId)
    source.runWith(sink)
  }

  private def getDocumentsSource(documentSetId: Long): Source[immutable.Seq[Document], akka.NotUsed] = {
    val query = documentsCompiled(documentSetId)
    val result = query.result.transactionally.withStatementParameters(fetchSize=nDocumentsPerDbFetch)
    val publisher: Publisher[Document] = database.slickDatabase.stream(result)
    Source.fromPublisher(publisher)
      .groupedWeightedWithin(nBufferBytes, writeInterval)(_.nBytesInMemoryEstimate)
  }

  private def getDocumentsSink(documentSetId: Long): Sink[immutable.Seq[Document], Future[Unit]] = {
    Sink.foldAsync(())((_, documents) => indexBatch(documentSetId, documents))
  }

  private def indexBatch(documentSetId: Long, documents: immutable.Seq[Document]): Future[Unit] = {
    indexClient.addDocuments(documentSetId, documents)
  }
}

object DbLuceneReindexer {
  lazy val singleton = new DbLuceneReindexer(LuceneIndexClient.onDiskSingleton)
}
