package steps

import anorm.SQL
import java.sql.Connection
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.openqa.selenium.WebDriver
import org.squeryl.{Session,SessionFactory}
import play.api.mvc.Call
import play.api.Play
import play.api.test.{TestBrowser,TestServer,FakeApplication}
import scala.language.implicitConversions

import org.overviewproject.database.{ DB, DataSource, DatabaseConfiguration }
import org.overviewproject.test.DbSetup
import models.OverviewDatabase

object TestDatabaseConfiguration extends DatabaseConfiguration {
  override val databaseDriver = "org.postgresql.Driver"
  override val databaseUrl = "jdbc:postgres://localhost/overview-test"
  override val username = "overview"
  override val password = "password"
}

object Framework {
  private class Tapper[A](obj: A) {
    def tap(f: (A) => Unit): A = {
      f(obj)
      obj
    }
  }
  private implicit def any2Tapper[A](obj: A) : Tapper[A] = new Tapper(obj)

  private var loaded = false
  private lazy val dataSource = new DataSource(TestDatabaseConfiguration)
  private lazy val application = FakeApplication()
  lazy val testServer = TestServer(3333).tap { _.start }
  lazy val browser : TestBrowser = TestBrowser.of(classOf[HtmlUnitDriver])

  def routeToUrl(call: Call) = {
    "http://localhost:3333" + call.url
  }

  private def dbWhenLoaded[A](block: (Connection) => A) : A = {
    OverviewDatabase.inTransaction {
      val connection = OverviewDatabase.currentConnection
      block(connection)
    }
  }

  def db[A](block: (Connection) => A) : A = {
    loadIfNotLoaded()
    dbWhenLoaded(block)
  }

  def loadIfNotLoaded() = {
    if (!loaded) {
      DB.connect(dataSource)
      System.setProperty("config.file", "conf/application-test.conf")
      Play.start(application)
      dbWhenLoaded { implicit connection => DbSetup.clearEntireDatabaseYesReally }
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
