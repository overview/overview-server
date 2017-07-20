package modules

import play.api.{Configuration,Environment}
import play.api.inject.{ApplicationLifecycle,Binding,Module}
import javax.inject.{Inject,Singleton}
import scala.concurrent.Future

import com.overviewdocs.database.Database

/** Provides a Database through dependency injection.
  *
  * Usage, in your class:
  *
  * class Doer @Inject() (database: Database) {
  *   ...
  * }
  *
  * On application shutdown, the database will be closed.
  */
@Singleton
class DatabaseModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(bind[Database].to[InjectedDatabase].in[Singleton])
  }
}

class InjectedDatabase @Inject() (
  lifecycle: ApplicationLifecycle
) extends Database(Database().slickDatabase) {
  lifecycle.addStopHook { () => slickDatabase.shutdown }
}
