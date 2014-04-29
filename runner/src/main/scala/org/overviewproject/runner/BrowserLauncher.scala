package org.overviewproject.runner

import java.awt.Desktop
import java.io.IOException
import java.net.{SocketTimeoutException, URL, URLConnection}

class BrowserLauncher(url: URL) {
  def waitForUrl: Unit = {
    var done = false
    while (!done) {
      try {
        val conn: URLConnection = url.openConnection
        conn.setConnectTimeout(500)
        conn.connect()
        done = true
      } catch {
        case e: SocketTimeoutException => Unit
        case e: IOException => Unit
      }
    }
  }

  def maybeOpenBrowserToUrl: Unit = {
    if (!Desktop.isDesktopSupported()) {
      return
    }

    val desktop = Desktop.getDesktop()

    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
      return
    }

    desktop.browse(url.toURI)
  }

  def createRunnable = new Runnable {
    def run() {
      waitForUrl
      maybeOpenBrowserToUrl
    }
  }
}

object BrowserLauncher {
  def apply(url: String) = new BrowserLauncher(new URL(url))
}
