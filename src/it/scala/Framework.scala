package steps

import anorm.SQL
import com.icegreen.greenmail.util.{ GreenMail, ServerSetupTest }
import java.sql.Connection
import java.util.concurrent.TimeUnit
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
  private var testMailServer : Option[GreenMail] = None
  private var worker : Option[WorkerProcess] = None

  /*
   * anorm looks to Play.current, which means we need to call Play.start() on
   * this thread. This application is *not* the one testServer uses. We *only*
   * use this application for direct database access within steps.
   */
  private var application : Option[FakeApplication] = None

  /** Accesses the test web browser, through FluentLenium.
    *
    * Use it like this:
    *
    *   val browser = Framework.browser
    *   browser.$("input[type=submit]").click()
    *
    * @see steps.BaseSteps.browser
    */
  def browser : TestBrowser = {
    testBrowser.getOrElse(throw new AssertionError("There is no loaded browser"))
  }

  /** Accesses the test SMTP server, through GreenMail.
    *
    * Use it like this:
    *
    *   val mailServer = Framework.mailServer
    *   mailServer.waitForIncomingEmail(1) must beTrue
    *   val message = mailServer.getReceivedMessages()(0)
    *   val body = GreenMailUtil.getBody(message)
    *   body mustContain("a certain string")
    *
    * @see steps.BaseSteps.mailServer
    */
  def mailServer : GreenMail = {
    testMailServer.getOrElse(throw new AssertionError("There is no loaded SMTP server"))
  }

  def clearDatabase = {
    OverviewDatabase.inTransaction {
      DbSetup.clearEntireDatabaseYesReally()(OverviewDatabase.currentConnection)
    }
  }

  /** Makes sure a worker is running for the duration of the scenario.
    *
    * This starts a separate sbt process. The only difference between this and
    * `sbt worker/run` is that here we connect to the test database.
    */
  def ensureWorker = {
    if (!worker.isDefined) {
      worker = Some(new WorkerProcess)
    }
    worker.map(_.start)
  }

  /** Makes OverviewDatabase.inTransaction() work.
    *
    * Tests will often want to write to the database or look for values in the
    * database, as opposed to querying by scanning HTML. This method makes that
    * possible.
    */
  private def setUpSessionDatabase = {
    if (!connected) {
      System.setProperty("config.file", "conf/application-it.conf")
      val dataSource = new DataSource(TestDatabaseConfiguration)
      DB.connect(dataSource)
      connected = true
      application = Some(FakeApplication())
      Play.start(application.getOrElse(throw new AssertionError))
    }
  }

  /** Starts a server on port 3333. */
  private def setUpTestServer = {
    System.setProperty("config.file", "conf/application-it.conf")
    testServer = Some(TestServer(3333))
    testServer.map(_.start)
  }

  /** Starts an SMTP server on port 3025. */
  private def setUpSmtpServer = {
    testMailServer = Some(new GreenMail(ServerSetupTest.SMTP))
    testMailServer.map(_.start)
  }

  /** Initializes Framework.browser.
    *
    * Tests use Framework.browser to interact with the test server.
    */
  private def setUpBrowser = {
    val browser = TestBrowser.firefox(Some("http://localhost:3333"))
    browser.manage.timeouts.setScriptTimeout(5, TimeUnit.SECONDS)
    testBrowser = Some(browser)
  }

  def setUp = {
    //setUpSessionDatabase
    setUpTestServer
    setUpSmtpServer
    clearDatabase
    setUpBrowser
  }

  def tearDown = {
    testServer.map(_.stop)
    testServer = None
    testMailServer.map(_.stop)
    testMailServer = None
    testBrowser.map(_.quit)
    testBrowser = None
    worker.map(_.stop)
    worker = None
    //application.map(_ => Play.stop)
    //application = None
  }

  def routeToUrl(call: Call) = call.url
}
