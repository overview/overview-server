package com.overviewdocs.nlp

import org.specs2.mutable.Specification

class StringTableSpec extends Specification {
  "StringTable" should {
    "basic operations" in {
      val st = new StringTable

      // Empty at start
      st.size should beEqualTo(0)

      val strs = Array("foo","bar","999","catatonia","excess")

      // no termIDs should succeed initially
      st.idToString(0) must throwA[IndexOutOfBoundsException]
      st.idToString(1) must throwA[IndexOutOfBoundsException]
      st.idToString(10) must throwA[IndexOutOfBoundsException]

      // add some strings, which should get sequential ids
      for (i <- (0 until strs.size))
        st.stringToId(strs(i)) must beEqualTo(i)
      st.size must beEqualTo(strs.size)

      // Now look them up
      for (i <- (0 until strs.size))
        st.idToString(i) must beEqualTo(strs(i))

      // Re-add a couple strings, size should not change
      st.stringToId(strs(0))
      st.stringToId(strs(2))
      st.stringToId(strs(strs.size-1))
      st.size must beEqualTo(strs.size)

      // stringToIDFailIfMissing must succeed if already added, fail if not
      st.stringToIdFailIfMissing(strs.head) must beEqualTo(0)
      st.stringToIdFailIfMissing("xyzzy") must throwA[NoSuchElementException]
    }
  }
}
