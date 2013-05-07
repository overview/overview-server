package org.overviewproject.documentcloud

import akka.actor._

class DocumentRetrieverGenerator(retrieverFactory: RetrieverFactory, maxDocuments: Int) extends RetrieverGenerator {
  import DocumentRetrieverProtocol._

  private var expectedRetrievals: Int = 0
  private var requestsSent: Int = 0
  private var totalRequests: Int = 0

  override def documentsToRetrieve: Int = expectedRetrievals
  override def totalDocuments: Int = totalRequests

  override def createRetrievers(result: SearchResult, receiver: ActorRef)(implicit context: ActorContext): Unit = {
    totalRequests = result.total
    expectedRetrievals = scala.math.min(totalRequests, maxDocuments)

    val numberOfNewRequests = scala.math.min(documentsToRetrieve - requestsSent, result.documents.size)
    spawnRetrievers(result.documents.take(numberOfNewRequests), receiver)
  }

  override def morePagesAvailable: Boolean = requestsSent < documentsToRetrieve

  private def spawnRetrievers(documents: Seq[Document], receiver: ActorRef)(implicit context: ActorContext): Unit = {
    requestsSent += documents.size

    documents.map { d =>
      val retriever = context.actorOf(Props(retrieverFactory.produce(d, receiver)))
      retriever ! Start()
    }
  }
}
