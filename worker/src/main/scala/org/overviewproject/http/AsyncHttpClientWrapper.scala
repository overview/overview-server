package org.overviewproject.http

import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Realm
import com.ning.http.client.Realm.AuthScheme
import com.ning.http.client.RequestBuilder

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

  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): Unit = {
    client.prepareGet(url).execute(responseHandler)
  }

  def submitWithAuthentication(url: String, credentials: Credentials, followRedirects: Boolean, responseHandler: AsyncCompletionHandler[Unit]): Unit = {

    val realm = new Realm.RealmBuilder()
      .setPrincipal(credentials.userName)
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