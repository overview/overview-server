package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.FileGroupJobHandlerProtocol._


class FileGroupJobHandlerSpec extends Specification {

  "FileGroupJobHandler" should {
    class TestJobHandler extends FileGroupJobHandler with MessageServiceComponent {

      var failureCallback: Option[Exception => Unit] = None
      var connectionCreationCount: Int = 0

      override val messageService = new MessageService {
        override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
          failureCallback = Some(failureHandler)
          connectionCreationCount += 1
          Success()
        }
      }

    }
    
    "start listening for messages" in new ActorSystemContext {
      val jobHandler = TestActorRef(new TestJobHandler)
      
      jobHandler ! ListenForFileGroupJobs
      
      jobHandler.underlyingActor.connectionCreationCount must be equalTo(1)
    }
    
    "restart connection if connection fails" in new ActorSystemContext {
      val jobHandler = TestActorRef(new TestJobHandler)
      val testJobHandler = jobHandler.underlyingActor
      
      jobHandler ! ListenForFileGroupJobs
      
      testJobHandler.failureCallback.map(f => f(new Exception("Connection failed")))
      
      awaitCond(testJobHandler.connectionCreationCount == 2)
      
    }

  }
}