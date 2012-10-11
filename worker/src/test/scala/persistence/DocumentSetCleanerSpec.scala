/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package persistence


import helpers.DbSetup._
import helpers.DbSpecification
import org.specs2.mutable.Specification

class DocumentSetCleanerSpec extends DbSpecification {
  step(setupDb)

  
  step(shutdownDb)
}
