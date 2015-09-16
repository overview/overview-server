package com.overviewdocs.http

import com.ning.http.client.{AsyncCompletionHandler,AsyncHttpClient=>NingAsyncHttpClient,AsyncHttpClientConfig,FluentCaseInsensitiveStringsMap,Realm,Request,RequestBuilder,Response=>NResponse}
import com.ning.http.client.Realm.AuthScheme
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.immutable.{Queue,TreeMap}
import scala.concurrent.{ExecutionContext,Future,Promise}

/**
 * Uses AsyncHttpClient to implement Client interface.
 */
class AsyncHttpClient extends Client {
  private val Timeout = 5 * 60 * 1000 // 5 minutes, to allow for downloading large files 
  private val Retries = 3
  @volatile private var shutdownWasCalled = false

  // We'll ensure we don't overload any given host. Rather than do what Ning
  // does (throw an exception), we'll use asynchronous programming to defer
  // the scheduling of the request until there's space for it.
  private val MaxConnectionsPerHost = 4

  // We'll just use `synchronized` to track our throttling
  private val connectionsPerHost = scala.collection.mutable.Map[String,Int]()
  private val throttledConnectionsPerHost = scala.collection.mutable.Map[String,Queue[Promise[Unit]]]() // when connectionsPerHost is full

  private def hostKey(request: Request): String = {
    val uri = request.getUri
    s"${uri.getScheme}://${uri.getHost}:${uri.getPort}"
  }

  private val builder = new AsyncHttpClientConfig.Builder()
  private val config = builder
    .setFollowRedirect(true)
    .setCompressionEnforced(true)
    .setAllowPoolingConnections(true)
    .setConnectTimeout(Timeout)
    .setRequestTimeout(Timeout)
    .setMaxRequestRetry(Retries)
    .build

  private val client = new NingAsyncHttpClient(config)

  private class PromiseAsyncCompletionHandler extends AsyncCompletionHandler[Unit] {
    private val promise = Promise[Response]()

    private def buildHeaders(in: FluentCaseInsensitiveStringsMap): Map[String,Seq[String]] = {
      var map = new TreeMap[String,Seq[String]]()(Ordering.by(_.toLowerCase))

      for (entry <- iterableAsScalaIterable(in)) {
        val seq: Seq[String] = iterableAsScalaIterable(entry.getValue).toSeq
        map += (entry.getKey -> seq)
      }

      map
    }

    val future = promise.future

    override def onThrowable(throwable: Throwable): Unit = {
      promise.failure(throwable)
    }

    override def onCompleted(ningResponse: NResponse): Unit = {
      promise.success(Response(
        ningResponse.getStatusCode,
        ningResponse.getResponseBodyAsBytes,
        buildHeaders(ningResponse.getHeaders)
      ))
    }
  }

  private def throttleRequest(request: Request)(implicit ec: ExecutionContext): Future[Unit] = {
    throttleKey(hostKey(request))
  }

  private def throttleKey(key: String): Future[Unit] = synchronized {
    val nConnections: Int = connectionsPerHost.getOrElse(key, 0)
    if (nConnections < MaxConnectionsPerHost) {
      connectionsPerHost.+=(key -> (nConnections + 1))
      Future.successful(())
    } else {
      // We'll return a Future that will be resolved later
      val queue: Queue[Promise[Unit]] = throttledConnectionsPerHost.getOrElse(key, Queue())
      val promise = Promise[Unit]()
      throttledConnectionsPerHost.+=(key -> queue.enqueue(promise))
      promise.future
    }
  }

  /** Mark a request as finished, so Futures from throttleKey() can complete.
    */
  private def unthrottleKey(key: String): Unit = synchronized {
    if (shutdownWasCalled) return

    throttledConnectionsPerHost.get(key) match {
      case Some(queue) => {
        // There's a queue. Assume our host is saturated.
        val (next, newQueue) = queue.dequeue
        next.success(()) // Let a throttled request begin
        if (newQueue.isEmpty) {
          throttledConnectionsPerHost.-=(key)
        } else {
          throttledConnectionsPerHost.+=(key -> newQueue)
        }
      }
      case None => {
        // Our host isn't saturated. Just decrement the counter.
        val nConnections: Int = connectionsPerHost(key) // Error if it isn't there
        if (nConnections == 1) {
          connectionsPerHost.-=(key)
        } else {
          connectionsPerHost.+=(key -> (nConnections - 1))
        }
      }
    }
  }

  override def get(url: String)(implicit ec: ExecutionContext): Future[Response] = {
    val request = new RequestBuilder("GET").setUrl(url).build
    execute(request)
  }

  override def get(url: String, credentials: Credentials, followRedirects: Boolean)(implicit ec: ExecutionContext): Future[Response] = {
    val realm = new Realm.RealmBuilder()
      .setPrincipal(credentials.username)
      .setPassword(credentials.password)
      .setUsePreemptiveAuth(true)
      .setScheme(AuthScheme.BASIC)
      .build

    val request = new RequestBuilder("GET").setUrl(url)
      .setFollowRedirects(followRedirects)
      .setRealm(realm)
      .build

    execute(request)
  }

  private def execute(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
    val key: String = hostKey(request)

    throttleKey(key).flatMap { _ =>
      if (shutdownWasCalled) {
        // Return any old Response. The caller is presumably logging failures,
        // so this is better than an Exception
        Future.successful(Response(200, Array[Byte](), Map()))
      } else {
        val handler = new PromiseAsyncCompletionHandler
        client.executeRequest(request, handler)
        handler.future.andThen { case _ => unthrottleKey(key) }
      }
    }
  }
  
  def shutdown: Unit = {
    if (shutdownWasCalled) return

    client.closeAsynchronously

    shutdownWasCalled = true

    // Clear all the Futures
    synchronized {
      for ((key, queue) <- throttledConnectionsPerHost) {
        queue.foreach(_.success(()))
      }
    }
  }
}
