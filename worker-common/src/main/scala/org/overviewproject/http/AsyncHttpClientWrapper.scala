package org.overviewproject.http

import java.util.concurrent.{ Future => JFuture }
import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient, AsyncHttpClientConfig, Realm}
import com.ning.http.client.Realm.AuthScheme

/**
 * Uses AsyncHttpClient to implement Client interface.
 */
class AsyncHttpClientWrapper extends Client {
  private val Timeout = 5 * 60 * 1000 // 5 minutes, to allow for downloading large files 

  private val builder = new AsyncHttpClientConfig.Builder()
  private val config = builder
    .setFollowRedirects(true)
    .setCompressionEnabled(true)
    .setAllowPoolingConnection(true)
    .setRequestTimeoutInMs(Timeout)
    .build

  private val client = new AsyncHttpClient(config)

  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): JFuture[Unit] = 
    client.prepareGet(url).execute(responseHandler)
  

  /**
   * @param followRedirects If `true`, AsyncHttpClient will pass along credentials when redirecting.
   */
  def submitWithAuthentication(url: String, credentials: Credentials, followRedirects: Boolean, responseHandler: AsyncCompletionHandler[Unit]): JFuture[Unit] = {

    val realm = new Realm.RealmBuilder()
      .setPrincipal(credentials.username)
      .setPassword(credentials.password)
      .setUsePreemptiveAuth(true)
      .setScheme(AuthScheme.BASIC)
      .build

    client.prepareGet(url)
      .setFollowRedirects(followRedirects)
      .setRealm(realm)
      .execute(responseHandler)
  }
  
  def shutdown(): Unit = client.close()
  
}