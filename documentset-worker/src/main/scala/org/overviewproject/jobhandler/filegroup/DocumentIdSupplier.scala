package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor

object DocumentIdSupplierProtocol {
  case class RequestIds(documentSetId: Long, numberOfIds: Int)
  case class IdRequestResponse(documentIds: Seq[Long])
}

/**
 * Responds to requests for document ids for a specified document set.
 * 
 * Multiple concurrent workers need ids for documents they are creating. [[DocumentIdSupplier]] 
 * is a source for serially created ids, ensuring that the ids generated do not overlap
 * with previously used ids.
 * Request responses are created by [[DocumentIdGenerator]]s, one for each document set id.
 * To avoid concurrent requests for the same document set id, the creation of a [[DocumentIdGenerator]]
 * blocks while reading the database to find the last document id used.
 * In this implementation, requests may block even when not strictly necessary - even if the actor receives
 * request for different document set ids, the actor will respond to a request before starting to process 
 * the next one. In the current setup with only two workers this unnecessary blocking should not cause too
 * much unneeded delay.
 * In this implementation the [[DocumentIdGenerator]] is never released, because the [[DocumentIdSupplier]]
 * does not know when the requested ids have actually been written to the database. Eventually the table of
 * [[DocumentIdGenerator]]s may consume all available memory, but in practice this won't be a problem (hopefully).
 * 
 */
trait DocumentIdSupplier extends Actor {
  import DocumentIdSupplierProtocol._
  
  def receive =  {
    case  RequestIds(documentSetId, numberOfIds) => {
      val documentIdGenerator = generators.getOrElseUpdate(documentSetId, createDocumentIdGenerator(documentSetId))
      
      sender() ! IdRequestResponse(documentIdGenerator.nextIds(numberOfIds))
    }
  }
  
  protected val generators: scala.collection.mutable.Map[Long, DocumentIdGenerator] = 
    scala.collection.mutable.Map.empty

  
  protected def createDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator
}