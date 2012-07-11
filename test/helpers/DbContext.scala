
package helpers

import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import com.avaje.ebean.Ebean
import org.specs2.mutable.BeforeAfter

trait DbContext extends BeforeAfter {
  
  def before : Any = {
    val application = FakeApplication();
       
    start(application)
    Ebean.beginTransaction
  }
            
  def after : Any = {
    Ebean.rollbackTransaction
    stop()
  }
    
}
