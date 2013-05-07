package org.overviewproject.documentcloud

import akka.actor._

class DocumentPageRetrieverGenerator(retrieverFactory: RetrieverFactory, maxDocuments: Int) extends RetrieverGenerator {
  import DocumentRetrieverProtocol._

  private var expectedRetrievals: Int = 0
  private var requestsSent: Int = 0
  private var totalRequests: Int = 0
  private var pagesRequested: Int = 0
  
  override def documentsToRetrieve: Int = expectedRetrievals
  override def totalDocuments: Int = totalRequests


  override def createRetrievers(result: SearchResult, receiver: ActorRef)(implicit context: ActorContext): Unit = {
    totalRequests = result.total
    expectedRetrievals = scala.math.min(totalRequests, maxDocuments)
    
    val documents = documentsToSplit(result.documents, pagesRequested)
    pagesRequested += documents.map(_.pages).sum
    
    if (documents.size < result.documents.size) expectedRetrievals = requestsSent + documents.size
    spawnRetrievers(documents, receiver)
  }

  override def morePagesAvailable: Boolean =  requestsSent < documentsToRetrieve

  private def documentsToSplit(documents: Seq[Document], totalPages: Int): Seq[Document] = 
    if (documents.isEmpty) Nil
    else if (totalPages >= maxDocuments) Nil
    else {
      val pagesFromNextDocument = scala.math.min(documents.head.pages, maxDocuments - totalPages)
      val nextDocument = documents.head.copy(pages = pagesFromNextDocument)
      
      nextDocument +: documentsToSplit(documents.tail, pagesFromNextDocument + totalPages)
    }

  
  private def spawnRetrievers(documents: Seq[Document], receiver: ActorRef)(implicit context: ActorContext): Unit = {
    requestsSent += documents.size
    
    documents.map { d =>
      val retriever = context.actorOf(Props(retrieverFactory.produce(d, receiver)))
      retriever ! Start()
    }
  }

}