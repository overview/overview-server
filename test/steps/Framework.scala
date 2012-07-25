package steps

import play.api.test.{TestBrowser,TestServer,FakeApplication}
import play.api.Play
import play.api.mvc.Call
import org.squeryl.PrimitiveTypeMode.transaction

import java.sql.Connection
import org.openqa.selenium.WebDriver
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.squeryl.{Session,SessionFactory}

object Framework {
  private val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"

  private class Tapper[A](obj: A) {
    def tap(f: (A) => Unit): A = {
      f(obj)
      obj
    }
  }
  private implicit def any2Tapper[A](obj: A) : Tapper[A] = new Tapper(obj)

  var loaded = false
  private lazy val application = FakeApplication()
  lazy val testServer = TestServer(3333).tap { _.start }
  lazy val browser : TestBrowser = TestBrowser.of(classOf[HtmlUnitDriver])

  def routeToUrl(call: Call) = {
    "http://localhost:3333" + call.url
  }

  def db[A](block: (Connection) => A) : A = {
    loadIfNotLoaded()

    val session = SessionFactory.newSession.tap { _.bindToCurrentThread }
    val ret = transaction {
      block(session.connection)
    }
    session.unbindFromCurrentThread
    ret
  }

  def loadIfNotLoaded() = {
    if (!loaded) {
      System.setProperty("db.default.url", testDatabaseUrl)
      Play.start(application)
      loaded = true
    }
  }

  //def stop() {
  //  browser.quit()
  //  testServer.stop()
  //  Play.stop(application)
  //  session.unbindFromCurrentThread
  //}
}
