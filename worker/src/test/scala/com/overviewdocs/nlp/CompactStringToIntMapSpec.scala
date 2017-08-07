/**
 * CompactPairArraySpec.scala
 *
 * Overview, created November 2012
 * @author Jonathan Stray
 *
 */
package com.overviewdocs.nlp

import org.specs2.mutable.Specification

class CompactStringToIntMapSpec extends Specification {
  "CompactStringToIntMap" should {
    "basic operations" in {
      // Must start empty
      val m = CompactStringToIntMap()
      m.size should beEqualTo(0)
      m.isEmpty should beTrue

      // Add some test data. Note use of negative numbers, and full 32 bit range
      val strs = Map("foo"->1,"bar"->2,"999"->(-10000),"catatonia"->65537,"excess"->(1<<31))
      strs foreach { m += _ }

      // Check element storage and retrieval via several different access methods
      m.size must beEqualTo(strs.size)                                // size
      strs foreach { case (k,v) => m.contains(k) must beTrue }        // contains
      strs foreach { case (k,v) => m.get(k) must beEqualTo(Some(v)) } // get
      strs foreach { case (k,v) => m(k) must beEqualTo(v) }           // apply
      m.toMap must beEqualTo(strs)                                    // matcher (whatever that uses...)

      // Now test element removal
      val delKey = strs.head._1
      m -= delKey
      m.contains(delKey) must beFalse
      m.size must beEqualTo(strs.size - 1)
      m.get(delKey) must beEqualTo(None)
      m(delKey) must throwA[NoSuchElementException]

      // empty must return a new map
      val m2 = m.empty
      m2 mustNotEqual(m)
      m2.isEmpty should beTrue

      // foreach
      var cnt = 0
      m foreach { case (k,v) =>
        strs(k) must beEqualTo(v)
        cnt += 1
      }
      cnt must beEqualTo(m.size)

      // iterator
      cnt = 0
      val i = m.iterator
      while (i.hasNext) {
        val (k,v) = i.next
        strs(k) must beEqualTo(v)
        cnt += 1
      }
      cnt must beEqualTo(m.size)
    }
  }
}
