package org.overviewproject.documentcloud

import akka.actor._

class DocumentSplitter(document: Document, receiver: ActorRef, retrieverGenerator: (Document, ActorRef) => Actor) extends Actor {
  import DocumentRetrieverProtocol._

  private var completedPages: Int = 0

  def receive = {
    case Start() if (document.pages > 0) => splitDocument
    case Start() => finish
    case JobComplete() => trackCompletions
  }

  private def splitDocument: Unit = for {
    pageNumber <- 1 to document.pages
    documentPage = new DocumentPage(document, pageNumber)
    pageRetriever = context.actorOf(Props(retrieverGenerator(documentPage, receiver)))
  } pageRetriever ! Start()

  private def trackCompletions: Unit = {
    completedPages += 1

    if (completedPages == document.pages) finish
  }

  private def finish: Unit = {
    context.parent ! JobComplete()
    context.stop(self)
  }
}