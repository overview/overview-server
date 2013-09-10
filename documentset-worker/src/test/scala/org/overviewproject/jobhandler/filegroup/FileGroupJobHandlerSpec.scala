package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol._
import akka.actor._
import org.overviewproject.test.ForwardingActor
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol.ExtractText
import org.overviewproject.jobhandler.MessageServiceComponent


class FileGroupJobHandlerSpec extends Specification {

  "FileGroupJobHandler" should {
    class TestJobHandler(handlerProbe: ActorRef) extends FileGroupJobHandler with MessageServiceComponent with TextExtractorComponent {

      var messageCallback: Option[String => Future[Unit]] = None
      var failureCallback: Option[Exception => Unit] = None
      var connectionCreationCount: Int = 0

      override val messageService = new MessageService {
        override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
          messageCallback = Some(messageDelivery)
          failureCallback = Some(failureHandler)
          connectionCreationCount += 1
          Success()
        }
      }
      
      val actorCreator = new ActorCreator {
        override def produceTextExtractor: Actor = new ForwardingActor(handlerProbe)
      }

    }
    
    "start listening for messages" in new ActorSystemContext {
      val fileHandler = TestProbe()
      val jobHandler = TestActorRef(new TestJobHandler(fileHandler.ref))
      
      jobHandler ! ListenForFileGroupJobs
      
      jobHandler.underlyingActor.connectionCreationCount must be equalTo(1)
    }
    
    "start file handler on incoming command" in new ActorSystemContext {
      val message = """{
        "cmd" : "process_file",
        "args" : {
          "documentSetId": 1,
          "fileId": 2
         }
      }"""
        
      val fileHandler = TestProbe()
      val jobHandler = TestActorRef(new TestJobHandler(fileHandler.ref))
      
      jobHandler ! ListenForFileGroupJobs
      val completion = jobHandler.underlyingActor.messageCallback.map(f => f(message))
      
      fileHandler.expectMsg(ExtractText(1, 2))
    }
    
    "restart connection if connection fails" in new ActorSystemContext {
      val fileHandler = TestProbe()
      val jobHandler = TestActorRef(new TestJobHandler(fileHandler.ref))
      val testJobHandler = jobHandler.underlyingActor
      
      jobHandler ! ListenForFileGroupJobs
      
      testJobHandler.failureCallback.map(f => f(new Exception("Connection failed")))
      
      testJobHandler.connectionCreationCount must be equalTo(2)
      
    }

  }
}