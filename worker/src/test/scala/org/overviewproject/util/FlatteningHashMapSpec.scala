package org.overviewproject.util

import scala.collection.mutable.{Map,IndexedSeq}
import scala.util.Random
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class FlatteningHashMapSpec extends Specification {
  // Basic flattener for Int->Long map
  implicit object IntLongFlattener extends KeyValueFlattener[Int,Long] {
    def flatSize = 3
    
    def flatten(kv:Entry, s:IndexedSeq[Int], i:Int) : Unit = {
      s(i) = kv._1
      flattenValue(kv._2, s,i)
    }
    
    def unFlatten(s:IndexedSeq[Int], i:Int) : Entry = {
      (s(i),unFlattenValue(s,i))
    }  
  
    def flattenValue(v:Long, s:IndexedSeq[Int], i:Int) : Unit = {
      s(i+1) = (v >>> 32).asInstanceOf[Int]
      s(i+2) = (v & 0xffffffff).asInstanceOf[Int]      
    }
    
    def unFlattenValue(s:IndexedSeq[Int], i:Int) : Long = {
      (s(i+1).asInstanceOf[Long] << 32) | s(i+2)
    } 

    def keyHashCode(e:Entry) : Int = e._1.hashCode
  
    def flatKeyHashCode(s:IndexedSeq[Int], i:Int) : Int = s(i).hashCode 

    def flatKeyEquals(k:Int, s:IndexedSeq[Int], i:Int) : Boolean = { k == s(i) }   
  }
  "FlatteningHashMap" should {
    trait EmptyTestSet extends Scope {
      var m = new FlatteningHashMap[Int, Long]()
    }

    "start empty" in new EmptyTestSet {
      m.size should beEqualTo(0)
      m.isEmpty should beTrue
    }

    trait SmallTestSet extends EmptyTestSet {
      // Add some test data. Note use of negative numbers, and full 32 bit range
      val data = Map(1->1L,2->999L,999->(-10000L),-65536->(1L<<40), 100000->(12345678L*78910112L))
      data.foreach( m += _)
    }

    "have .size" in new SmallTestSet {
      m.size must beEqualTo(data.size)
    }

    "have .contains" in new SmallTestSet {
      forall(data.keys) { m.contains(_) must beTrue }
      m.contains(12) must beFalse
    }

    "have .get" in new SmallTestSet {
      forall(data.keys) { k => m.get(k) must beEqualTo(data.get(k)) }
      data.get(12) must beNone
    }

    "have .apply" in new SmallTestSet {
      forall(data.keys) { k => m.get(k) must beEqualTo(data.get(k)) }
      data(12) must throwA[NoSuchElementException]
    }

    "remove elements" in new SmallTestSet {
      val delKey = data.head._1 
      m -= delKey
      m.contains(delKey) must beFalse
      m.size must beEqualTo(data.size - 1)
      m.get(delKey) must beEqualTo(None)
      m(delKey) must throwA[NoSuchElementException]
    }

    "have .empty" in new SmallTestSet {
      val m2 = m.empty
      m2 mustNotEqual(m)
      m2.isEmpty must beTrue
    }

    "have .foreach" in new SmallTestSet {
      var out = Map[Int,Long]()
      m.foreach(out += _)
      out must beEqualTo(data)
    }

    "have .iterator" in new SmallTestSet {
      var out = Map[Int,Long]()
      val i = m.iterator
      while (i.hasNext) {
        out += i.next
      }
      out must beEqualTo(data)
    }

    "have -=" in new SmallTestSet {
      m -= 1
      m.toMap must beEqualTo(data - 1)
    }
  }
}
