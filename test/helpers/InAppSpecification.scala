package test.helpers

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.test.FakeApplication
import play.api.Play.{start,stop}

/** A test environment that starts/stops a FakeApplication.
  *
  * You need this for every test of every class that uses Play's globals.
  */
trait InAppSpecification extends Specification with BeforeAfterAll {
  private lazy val app = FakeApplication()

  override def beforeAll = start(app)
  override def afterAll = stop(app)
}
