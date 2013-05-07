package org.overviewproject.documentcloud

import akka.actor._

trait RetrieverGenerator {
  def createRetrievers(result: SearchResult, receiver: ActorRef)(implicit context: ActorContext): Unit
  def morePagesAvailable: Boolean
  def documentsToRetrieve: Int
  def totalDocuments: Int
}
