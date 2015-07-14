package modules

import com.zaxxer.hikari.HikariDataSource
import javax.inject._
import play.api.inject.ApplicationLifecycle
import play.api.{Mode,Play}
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
  * Load this module, and the database will shut down at the right time. It
  * will only register its shutdown hook in dev mode, because dev is the only
  * mode that leaks a ClassLoader. (Test, in particular, starts up and shuts
  * down dozens of Applications, all in the same ClassLoader; and then the
  * process, which was forked, ends. So we definitely don't want to shut down
  * in Test mode.)
  *
  * And why is this file so complicated? Because I don't really understand Play
  * modules, and I'm not in the mood to. Just scratching an itch here!
  */
@Singleton
class DbConnection @Inject() (lifecycle: ApplicationLifecycle) {
  val dataSource: HikariDataSource = DB.dataSource

  if (Play.current.mode == Mode.Dev) { // Either `current` is set, or the caller shouldn't have called this
    lifecycle.addStopHook { () => Future.successful(dataSource.shutdown) }
  }
}
