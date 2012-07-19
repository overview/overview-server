import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.internals.DatabaseAdapter
import org.squeryl.{Session, SessionFactory}
import play.api.db.DB
import play.api.GlobalSettings

import play.api.Application

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    SessionFactory.concreteFactory = Some( () =>
      Session.create(DB.getDataSource()(app).getConnection(), new PostgreSqlAdapter())
    )
  }
}
