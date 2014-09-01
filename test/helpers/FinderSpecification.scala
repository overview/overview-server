package models.orm.finders

import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.{AroundExample, Fragments, Scope, Step}
import org.squeryl.Session
import play.api.test.FakeApplication
import play.api.Play.{start,stop}

import models.OverviewDatabase

/** A test environment for finders.
 *
 * These tests are slow, but they can catch lots of errors. Ugh, pragmatism.
 *
 * Usage:
 *
 *   class SessionFinderSpec extends FinderSpecification {
 *     trait SomethingScope extends FinderScope {
 *       // Insert stuff into the database. It starts off empty.
 *       val savedSession = schema.sessions.insert(Session(...))
 *     }
 *
 *     "it should find the session" in new SomethingScope {
 *       SessionFinder.byId(savedSession.id).headOption must beSome(savedSession)
 *     }
 *   }
 */
class FinderSpecification extends Specification with AroundExample {
  sequential

  trait FinderScope extends Scope {
    val schema = models.orm.Schema
  }

  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }

  override def around[T: AsResult](test: => T) = OverviewDatabase.inTransaction { 
    try {
      AsResult(test)
    } finally {
      Session.currentSession.connection.rollback()
    }
  }
}
