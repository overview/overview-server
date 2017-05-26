import play.api.{Application,GlobalSettings}

import modules.DbConnection

object Global extends GlobalSettings {
  private def connectToDatabaseOnce(app: Application) = app.injector.instanceOf[DbConnection]

  override def onStart(app: Application) = connectToDatabaseOnce(app)
}
