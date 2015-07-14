package modules

import com.zaxxer.hikari.HikariDataSource
import javax.inject._
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

import org.overviewproject.database.DB


/** Provides a DataSource.
  *
  * Why does this exist? It just proxies DB.dataSource, after all. Because ...
  * it's complicated:
  *
  * When running Play in dev mode, the Play sbt plugin will simply dereference
  * the application's ClassLoader and create another. That leaks every
  * singleton in the old ClassLoader. So DB.dataSource will stay open forever.
  *
  * Load this module, and the database will shut down at the right time.
  *
  * And why is this file so complicated? Because I don't really understand Play
  * modules, and I'm not in the mood to. Just scratching an itch here!
  */
@Singleton
class DbConnection @Inject() (lifecycle: ApplicationLifecycle) {
  val dataSource: HikariDataSource = DB.dataSource

  lifecycle.addStopHook { () => Future.successful(dataSource.shutdown) }
}
