package plugins

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }
import scala.concurrent.{Await,Future,Promise}
import scala.concurrent.duration.Duration
import org.fusesource.hawtbuf.{Buffer => HawtBuffer}
import org.fusesource.stomp.codec.StompFrame
import org.fusesource.stomp.client.{Callback,CallbackConnection,Stomp}
import org.fusesource.stomp.client.Constants.{CONTENT_TYPE,DESTINATION,SEND,TRANSFORMATION}

import play.api.{ Application, Logger, Play }
import play.api.Play.current
import play.api.Plugin
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/** Message queue config values set in application.conf */
object MessageQueueConfiguration {
  private val QueuePrefix = "message_queue"

  val BrokerUri = configValue("broker_uri")
  val Username = configValue("username")
  val Password = configValue("password")
  val DocumentSetCommandQueueName = configValue("queue_name")
  val FileGroupCommandQueueName = configValue("file_group_queue_name")
  val ClusteringCommandQueueName = configValue("clustering_queue_name")
  
  private def configValue(key: String): String =
    Play.current.configuration.getString(s"$QueuePrefix.$key")
      .getOrElse(throw new Exception(s"Unable to read message_queue configuration for $key"))
}

/**
 * Plugin that manages a connection to the Message Broker.
 * Set `message_queue.mock=true` in application.conf to mock out
 * the connection.
 */
class StompPlugin(application: Application) extends Plugin {
  private def useMock: Boolean = Play.current.configuration.getBoolean("message_queue.mock").getOrElse(false)
  private lazy val client: MessageQueueClient = {
    if (useMock) {
      new MockMessageQueueClient
    } else {
      new StompMessageQueueClient
    }
  }

  lazy val documentSetCommandQueue = new MessageQueueDestination(client, MessageQueueConfiguration.DocumentSetCommandQueueName)
  lazy val fileGroupCommandQueue = new MessageQueueDestination(client, MessageQueueConfiguration.FileGroupCommandQueueName)
  lazy val clusteringCommandQueue = new MessageQueueDestination(client, MessageQueueConfiguration.ClusteringCommandQueueName)

  override def onStart(): Unit = { client }
  override def onStop(): Unit = { client.close }
}

class MessageQueueDestination(client: MessageQueueClient, queueName: String) {
  /** Send a message.  */
  def send(messageText: String): Future[Unit] = client.send(queueName, messageText, None)

  /** Send a message to the `messageGroup`. */
  def send(messageText: String, messageGroup: String): Future[Unit] = client.send(queueName, messageText, Some(messageGroup))
}

trait MessageQueueClient {
  /** Sends a message. */
  def send(queueName: String, messageText: String, messageGroup: Option[String]) : Future[Unit]

  /** Closes the message queue.
    *
    * After closing, you cannot restart it.
    */
  def close: Unit
}

class StompMessageQueueClient extends MessageQueueClient {
  private val ReconnectDelay = Duration(1, "second")
  private val CloseTimeout = Duration(100, "ms")

  private var connection: Future[CallbackConnection] = connect

  private def connect: Future[CallbackConnection] = {
    val promise = Promise[CallbackConnection]()

    def tryConnect: Unit = {
      val uri = MessageQueueConfiguration.BrokerUri
      val stomp = new Stomp(uri)
      stomp.setLogin(MessageQueueConfiguration.Username)
      stomp.setPasscode(MessageQueueConfiguration.Password)

      stomp.connectCallback(new Callback[CallbackConnection] {
        override def onFailure(e: Throwable) = {
          Logger.warn(s"Failed to connect to message broker at ${uri} (will retry): ${e.getMessage()}") // Don't log to error to avoid generating error emails during startup
          Akka.system.scheduler.scheduleOnce(ReconnectDelay) { tryConnect }
        }
        override def onSuccess(v: CallbackConnection) = {
          Logger.info(s"Connected to message broker at ${uri}")
          promise.trySuccess(v) // Our Stomp client sometimes calls its callbacks multiple times
        }
      })
    }

    tryConnect

    promise.future
  }

  private def reconnect: Future[CallbackConnection] = {
    connection.map(_.close(null)) // kill threads -- don't wait for them
    connection = connect
    connection
  }

  def send(queueName: String, messageText: String, messageGroup: Option[String] = None): Future[Unit] = {
    val frame = new StompFrame(SEND)
    frame.addHeader(DESTINATION, StompFrame.encodeHeader(queueName))
    frame.addHeader(CONTENT_TYPE, StompFrame.encodeHeader("application/json"))
    //frame.addHeader(TRANSFORMATION, StompFrame.encodeHeader("jms/text-message"))
    for (g <- messageGroup) {
      frame.addHeader(StompFrame.encodeHeader("message_group"), StompFrame.encodeHeader(g))
    }
    frame.content(new HawtBuffer(messageText.getBytes("UTF-8")))

    val promise = Promise[Unit]()

    def trySend(nRetries: Int): Unit = {
      connection.map(_.send(frame, new Callback[Void] {
        override def onFailure(e: Throwable) = {
          Logger.warn(s"[$queueName] failed to send message. ${nRetries} retries left: ${e.getMessage()}", e)
          if (nRetries > 1) {
            reconnect.onSuccess { case _ => trySend(nRetries - 1) }
          } else {
            promise.tryFailure(e) // Stomp client sometimes calls these multiple times
          }
        }
        override def onSuccess(u: Void) = promise.trySuccess(Unit) // Stomp client sometimes calls these multiple times
      }))
    }

    trySend(2)

    promise.future
  }

  private def closeAsync = Future[Unit] {
    val promise = Promise[Unit]()
    connection.map(_.close(new Runnable { def run() = promise.success(Unit) }))
    promise.future
  }

  def close: Unit = Await.result(closeAsync, CloseTimeout)
}

class MockMessageQueueClient extends MessageQueueClient {
  override def send(queueName: String, messageText: String, messageGroup: Option[String]) = Future.successful(Unit)
  override def close = {}
}
