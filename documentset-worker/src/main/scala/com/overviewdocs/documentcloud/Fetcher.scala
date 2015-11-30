package com.overviewdocs.documentcloud

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Fetcher(server: DocumentCloudServer) {
  /** Fetches documents from DocumentCloud and writes them to a writer.
    *
    * You're free to run this concurrently: that is, set up many fetches on the
    * same document set. For instance:
    *
    *   val producer = new HeaderProducer(...)
    *   val writer = new DocumentWriter(...)
    *   val fetcher = new Fetcher
    *   val done = Future.sequence(Seq(
    *     fetcher.run(producer, writer),
    *     fetcher.run(producer, writer),
    *     fetcher.run(producer, writer)
    *   ))
    */
  def run(producer: HeaderProducer, writer: DocumentWriter): Future[Unit] = {
    def continue = run(producer, writer)
    def end = Future.successful(())

    producer.next.flatMap(_ match {
      case HeaderProducer.Skip(n) => writer.skip(n); continue
      case HeaderProducer.Fetch(header) => {
        fetchAndWrite(header, producer.username, producer.password, writer)
          .flatMap(_ => continue)
      }
      case HeaderProducer.End => end
    })
  }

  /** Fetches one document, and writes it (or its error) to the writer. */
  private def fetchAndWrite(
    header: DocumentCloudDocumentHeader,
    username: String,
    password: String,
    writer: DocumentWriter
  ): Future[Unit] = {
    server.getText(header.textUrl, username, password, header.access).map(_ match {
      case Left(message) => writer.addError(header.documentCloudId, message)
      case Right(text) => writer.addDocument(header.toDocument(text))
    })
  }
}
