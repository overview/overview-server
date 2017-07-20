package test.helpers

import play.api.{Application,Environment,Mode}
import play.api.inject.guice.GuiceApplicationBuilder
import akka.stream.Materializer
import org.specs2.mutable.Specification
import play.api.Play.{start,stop}

/** A test environment that starts/stops a fake Application.
  *
  * You need this for every test of every class that uses Play's globals.
  * Globals are deprecated, but implicit Materializer is useful.
  */
trait InAppSpecification extends Specification {
  protected lazy val app = InAppSpecification.app
  protected implicit def materializer: Materializer = app.materializer
}

object InAppSpecification {
  private lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment(new java.io.File("."), classOf[InAppSpecification].getClassLoader, Mode.Test))
    .configure(
      "akka.actor.provider" -> "local",
      "play.allowGlobalApplication" -> false
    )
    .build
}
