package org.overviewproject.util

import scala.collection.mutable.{Map,IndexedSeq}
import scala.util.Random
import org.specs2.mutable.Specification
import org.specs2.specification._

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
    
    "small test set" in {
      // Must start empty
      val m = new FlatteningHashMap[Int, Long]()
      
      m.size should beEqualTo(0)
      m.isEmpty should beTrue
            
      // Add some test data. Note use of negative numbers, and full 32 bit range
      val data = Map(1->1L,2->999L,999->(-10000L),-65536->(1L<<40), 100000->(12345678L*78910112L))
      data foreach { m += _ }
      
      // Check element storage and retrieval via several different access methods
      m.size must beEqualTo(data.size)                                // size
      m.contains(data.head._1) must beTrue
      
      data foreach { case (k,v) => m.contains(k) must beTrue }        // contains
      data foreach { case (k,v) => m.get(k) must beEqualTo(Some(v)) } // get
      data foreach { case (k,v) => m(k) must beEqualTo(v) }           // apply
      m must haveTheSameElementsAs(data)                              // matcher (whatever that uses...)

      // Now test element removal
      val delKey = data.head._1 
      m -= delKey
      m.contains(delKey) must beFalse
      m.size must beEqualTo(data.size - 1)
      m.get(delKey) must beEqualTo(None)
      m(delKey) must throwA[NoSuchElementException]

      // empty must return a new map
      val m2 = m.empty
      m2 mustNotEqual(m)
      m2.isEmpty should beTrue
      
      // foreach
      var cnt = 0
      m foreach { case (k,v) =>
        data(k) must beEqualTo(v)
        cnt += 1
      }
      cnt must beEqualTo(m.size)
      
      // iterator
      cnt = 0
      val i = m.iterator
      while (i.hasNext) {
        val (k,v) = i.next
        data(k) must beEqualTo(v)
        cnt += 1
      }
      cnt must beEqualTo(m.size)
    }
    
    
    "large test set" in {
      val m = new FlatteningHashMap[Int, Long]()
      val ref = Map[Int,Long]()
      
      val r = new Random(555) // arbitrary seed
      
      // Add 100k random elements
      val sz = 100000
      for (i <- 0 until sz) {
        val elem = (r.nextInt, r.nextLong) 
        m += elem
        m.contains(elem._1) should beTrue
        ref += elem
      }
      
      m.size should beEqualTo(ref.size)
      ref foreach { e => m.contains(e._1) should beTrue }           // got all of that?
      m foreach { e => ref.contains(e._1) should beTrue }           // don't make up elements, okay?
      
      // delete 30%
      var delThresh = 0.3
      m foreach { case (k,v) =>
        m.contains(k) should beTrue
        if (r.nextFloat < delThresh) {
          m -= k
          m.contains(k) should beFalse
          ref -= k
        }
      }

      m.size should beEqualTo(ref.size)
      ref foreach { e => m.contains(e._1) should beTrue }
      m foreach { e => ref.contains(e._1) should beTrue }

      // now do a mixed sequence of adds and deletes
      var addThresh = 0.2
      m foreach { case (k,v) =>
        m.contains(k) should beTrue
        if (r.nextFloat < delThresh) {
          m -= k
          m.contains(k) should beFalse
          ref -= k
        }
        if (r.nextFloat < addThresh) {
          val elem = (r.nextInt, r.nextLong) 
          m += elem
          m.contains(elem._1) should beTrue
          ref += elem
        }
      }
      
      m.size should beEqualTo(ref.size)
      ref foreach { e => m.contains(e._1) should beTrue }
      m foreach { e => ref.contains(e._1) should beTrue }
    }
  }
}