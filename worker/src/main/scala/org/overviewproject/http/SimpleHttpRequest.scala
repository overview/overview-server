
package org.overviewproject.http

import akka.dispatch.ExecutionContext

import com.ning.http.client._
import com.ning.http.client.Response
import com.ning.http.client.Realm.AuthScheme

class SimpleHttpRequest extends AsyncHttpRetriever {

  private def getHttpConfig(followRedirects: Boolean = true) = {
    val builder = new AsyncHttpClientConfig.Builder

    builder.
      setFollowRedirects(followRedirects).
      setCompressionEnabled(true).
      setAllowPoolingConnection(true).
      setRequestTimeoutInMs(5 * 60 * 1000).
      build
  }

  private lazy val asyncHttpClient = new AsyncHttpClient(getHttpConfig(followRedirects = false))

  def request(resource: DocumentAtURL,
    onSuccess: Response => Unit, onFailure: Throwable => Unit): Unit = {

    val responseHandler = new AsyncCompletionHandler[Response]() {
      override def onCompleted(response: Response) = {
        onSuccess(response)
        response
      }
      override def onThrowable(t: Throwable) = {
        onFailure(t)
      }
    }

    resource match {
      case r: DocumentAtURL with BasicAuth => getWithBasicAuth(r, responseHandler)
      case _ => asyncHttpClient.prepareGet(resource.textURL).execute(responseHandler)
    }
  }

  lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())

  private def getWithBasicAuth(resource: DocumentAtURL with BasicAuth,
    responseHandler: AsyncCompletionHandler[Response]) = {

    val realm = new Realm.RealmBuilder()
      .setPrincipal(resource.username)
      .setPassword(resource.password)
      .setUsePreemptiveAuth(true)
      .setScheme(AuthScheme.BASIC)
      .build();

    asyncHttpClient.prepareGet(resource.textURL).setRealm(realm).execute(responseHandler)
  }

  // TODO: get rid of this. Only used to support size method in DocumentCloudDocumentSource
  def blockingHttpRequest(url: String): String = {
    val f = asyncHttpClient.prepareGet(url).execute()
    f.get().getResponseBody()
  }

}
