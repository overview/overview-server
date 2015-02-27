package org.overviewproject.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DocumentIdRequest(numberOfIds: Long)

trait RequestDocumentIds extends TaskStep {
   protected val documentIdSupplier: ActorRef
   protected val document: PdfFileDocumentData
   
   protected def nextStepForResponse(documentsetIds: Seq[Long]): TaskStep
   
   override def execute: Future[TaskStep] = Future {
     documentIdSupplier ! DocumentIdRequest(1)
     
     WaitForResponse(nextStepForResponse)
   }
}