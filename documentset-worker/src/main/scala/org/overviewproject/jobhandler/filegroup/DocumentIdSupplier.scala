package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor

object DocumentIdSupplierProtocol {
  case class RequestIds(documentSetId: Long, numberOfIds: Int)
  case class IdRequestResponse(documentIds: Seq[Long])
}

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