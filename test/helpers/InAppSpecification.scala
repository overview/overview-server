package test

import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import play.api.test.FakeApplication
import play.api.Play.{start,stop}

/** A test environment that starts/stops a FakeApplication.
  *
  * You need this for every test of every class that connects to the database.
  */
trait InAppSpecification extends Specification {
  sequential

  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }
}
