package test.helpers

import play.api.{Application,Environment,Mode}
import play.api.inject.guice.GuiceApplicationBuilder
import akka.stream.Materializer
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.Play.{start,stop}

/** A test environment that starts/stops a fake Application.
  *
  * You need this for every test of every class that uses Play's globals.
  * Globals are deprecated, but implicit Materializer is useful.
  */
trait InAppSpecification extends Specification with BeforeAfterAll {
  protected lazy val app = InAppSpecification.app
  protected implicit def materializer: Materializer = app.materializer

  override def beforeAll = start(app)

  // HACK: Play's start(app) automatically calls stop(previousApp), which we
  // don't want because it's expensive. stop(null) sets previousApp=null and
  // avoids all shutdown. However, that means we'll call start many times and
  // only shut down once -- good thing we're using @Singletons for everything!
  override def afterAll = stop(null)
}

object InAppSpecification {
  private lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment(new java.io.File("."), classOf[InAppSpecification].getClassLoader, Mode.Test))
    .build
}
