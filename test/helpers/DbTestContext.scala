package helpers

import anorm._

import org.specs2.mutable.BeforeAfter

import play.api.db._
import play.api.Play.{start, stop, current}
import play.api.test._


trait  DbTestContext extends BeforeAfter {
  val application = FakeApplication()
	
  def before : Any = {
    start(application)
  }
  
  def after : Any = {
    DB.withConnection { implicit connection =>
      SQL("truncate table node cascade").execute()
    }
    stop()
  }
}