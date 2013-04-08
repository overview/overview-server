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
  private var connected = false
  private var testServer : Option[TestServer] = None
  private var testBrowser : Option[TestBrowser] = None

  /*
   * anorm looks to Play.current, which means we need to call Play.start() on
   * this thread. This application is *not* the one testServer uses. We *only*
   * use this application for direct database access within steps.
   */
  private var application : Option[FakeApplication] = None

  def browser = {
    testBrowser.getOrElse(throw new AssertionError("There is no loaded browser"))
  }

  def clearDatabase = {
    OverviewDatabase.inTransaction {
      DbSetup.clearEntireDatabaseYesReally()(OverviewDatabase.currentConnection)
    }
  }

  /** Makes OverviewDatabase.inTransaction() work.
    *
    * Tests will often want to write to the database or look for values in the
    * database, as opposed to querying by scanning HTML. This method makes that
    * possible.
    */
  private def setUpSessionDatabase = {
    if (!connected) {
      System.setProperty("config.file", "conf/application-test.conf")
      val dataSource = new DataSource(TestDatabaseConfiguration)
      DB.connect(dataSource)
      connected = true
      application = Some(FakeApplication())
      Play.start(application.getOrElse(throw new AssertionError))
    }
  }

  /** Starts a server on port 3333. */
  private def setUpTestServer = {
    System.setProperty("config.file", "conf/application-test.conf")
    testServer = Some(TestServer(3333))
    testServer.map(_.start)
  }

  /** Initializes Framework.browser.
    *
    * Tests use Framework.browser to interact with the test server.
    */
  private def setUpBrowser = {
    testBrowser = Some(TestBrowser.firefox(Some("http://localhost:3333")))
  }

  def setUp = {
    //setUpSessionDatabase
    setUpTestServer
    clearDatabase
    setUpBrowser
  }

  def tearDown = {
    testServer.map(_.stop)
    testServer = None
    testBrowser.map(_.quit)
    testBrowser = None
    //application.map(_ => Play.stop)
    //application = None
  }

  def routeToUrl(call: Call) = {
    "http://localhost:3333" + call.url
  }
}
