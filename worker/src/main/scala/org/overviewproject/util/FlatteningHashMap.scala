/**
 * FlatteningHashMap
 * Memory efficient hashmap, for any class for which you can provide a flattener
 * that can flatten to a fixed number of Ints 
 *
 * @author Jonathan Stray
 * Created April 2013
 *
 */

package org.overviewproject.util

import scala.collection.mutable.{IndexedSeq,ArrayBuffer,Map,MapLike}

// Hash Map that uses absolutely minimal storage -- all arrays, no object overhead
// However, can only store keys/values that can be serialized to/from a fixed number of Int's

// Base class for flattener 
// K is key type. E is entry type. It's same as key type K for a set, but Pair[K,V] for Map
abstract class KeyFlattener[K, E] {
  type Entry = E
  
  def flatSize: Int
  
  // Write an entry to a sequence of totalSize Ints, at given offset
  def flatten(entry:E, toSeq:IndexedSeq[Int], atOffset:Int) : Unit
  
  // Create a new object from a sequence of totalSize ints, at given offset
  def unFlatten(fromSeq:IndexedSeq[Int], atOffset:Int) : E  
  
  // Compute hash code
  // If E is a Pair[A,B], for use in a map, this must compute hash code of key only
  def keyHashCode(entry:E) : Int
  
  // compute hash code from flattened representation
  // efficiency method, must return equivalent of keyHashCode(unFlatten(toSeq, atOffset))
  def flatKeyHashCode(toSeq:IndexedSeq[Int], atOffset:Int) : Int 

  // compare key of self to key stored as ints
  // efficiency method, must return equivalent of e == unFlatten(toSeq, atOffset)
  def flatKeyEquals(k:K, toSeq:IndexedSeq[Int], atOffset:Int) : Boolean 
}

// K = key type, V = value type 
// We extend KeyFlattener, with entry type Pair[K,V]. 
// Add method to unflatten only the value
// But keyHashCode etc. still needs to operate only on Key, not value!
abstract class KeyValueFlattener[K, V] extends KeyFlattener[K, Pair[K,V]] {
  
  def flattenValue(v: V, toSeq:IndexedSeq[Int], atOffset:Int) : Unit
  def unFlattenValue(fromSeq:IndexedSeq[Int], atOffset:Int) : V  
} 


// Contains all the basic logic for CompactIntHashSet / CompactIntHashMap
// This is a growable, linked list chaining hash table
// K = type of of Key (e.g, Int)
// E = type of of what we store in a table entry
// E will be K for a set, or Pair[K, value] for a map 
abstract class FlatteningHashTable[K,E] {
    
  // derived classes must provider flattener
  protected def entryFlattener:KeyFlattener[K,E]
  
  // Configuration 
  protected val initialSize = 16

  // Linked list end marker (can't use 0 because it's a valid index)
  protected final val endOfList = -1
  
  protected val entrySize = entryFlattener.flatSize + 1          // +1 for our "next" pointer

  // Istate
  private var table = Array.fill[Int](initialSize)(endOfList)                       // hash table entries 
  protected val storage = ArrayBuffer.fill[Int](initialSize * entrySize)(endOfList)   // entry storage     
    
  private var firstFreeIndex = endOfList                    // linked list of free entries
  private var highWaterIndex = 0                            // all entries from here to storage.size are also free
  
  // Given an index to the start of an entry, where is the data?
  protected def indexToData(i:Int) = i+1    // data stored immediately following first int, which is the "next" entry pointer

  // Given an index, rehydryate an entry
  protected def unFlattenEntry(i:Int) = 
    entryFlattener.unFlatten(storage, indexToData(i))
 
  // Linked list management. 
  // Given an index to an entry, get or set the "next" entry pointer. Simple, because the index points to next ptr
  protected def getNext(i:Int) = { require(storage(i)!=i); storage(i) }    
  protected def setNext(i:Int, n:Int) = storage(i) = n
  
  // Hash code for an entry object, and an entry in our storage
  def keyHashCode(key:K) = key.hashCode
  def entryHashCode(entry:E) = entryFlattener.keyHashCode(entry)
  def storedHashCode(i:Int) = entryFlattener.flatKeyHashCode(storage, indexToData(i))
  def storedKeyEquals(i:Int, key:K) = entryFlattener.flatKeyEquals(key, storage, indexToData(i)) 
  
  // --- Storage memory management ---
  
  // returns index of a new entry, initialized with given object
  def allocateEntry(entry:E) : Int = {
    //println(s"Allocating entry for $entry")
    var f = 0
    if (firstFreeIndex != endOfList) {
      //println("Allocating via free list")
      f = firstFreeIndex
      firstFreeIndex = getNext(firstFreeIndex)      // advance pointer
    } else {
      //println(s"Allocating via high water at $highWaterIndex")
      if (highWaterIndex < storage.size)
        storage ++= Seq.fill(entrySize)(endOfList)  // create entrySize new elements
      f = highWaterIndex
      highWaterIndex += entrySize
    }
    entryFlattener.flatten(entry, storage, indexToData(f))
    f
  }
  
  // Free an entry, by placing it at the head of the free list
  def freeEntry(index:Int) : Unit = {
    setNext(index, firstFreeIndex)
    firstFreeIndex = index
  }
  
  // --- Modified from HashTable.scala ---
  
  private def lastPopulatedIndex = {
    var idx = table.length - 1
    while (table(idx) == endOfList && idx > 0)
      idx -= 1
    idx
  }
  
  protected var _loadFactor = defaultLoadFactor

  /** The number of mappings contained in this hash table.
   */
  protected var tableSize: Int = 0

  /** The next size value at which to resize (capacity * load factor).
   */
  protected var threshold: Int = initialThreshold(_loadFactor)

  protected def tableSizeSeed = Integer.bitCount(table.length - 1)
  protected var seedvalue: Int = tableSizeSeed

  private def initialThreshold(_loadFactor: Int): Int = newThreshold(_loadFactor, initialCapacity)
  
  private def initialCapacity = capacity(initialSize)

   /** Find entry with given key in table, returning index or endOfList if not found.
   */
  protected def findEntry(key: K): Int = {
    findEntry0(key, index(keyHashCode(key)))
  }
  
  protected def findEntry0(key:K, h:Int) : Int = {
    var i = table(h)
    while (i != endOfList && !storedKeyEquals(i, key)) i = getNext(i)
    i
  }
     
  /** Add entry to table
   *  pre: no entry with same key exists
   */
  protected def addEntry(entry: E) : Unit = {
    addEntry0(entry, index(entryHashCode(entry)))
  }
  
  protected def addEntry0(entry: E, h:Int) : Unit = {
    val newEntry = allocateEntry(entry)
    setNext(newEntry, table(h))
    table(h) = newEntry
    tableSize = tableSize + 1
    if (tableSize > threshold)
      resize(2 * table.length)
    //println(s"Stored $entry addr $newEntry in hash chain $h")
  }
  
  /** Remove entry from table if present. Returns true if entry was found
   */
  protected def removeEntry(key: K) : Boolean = {
    //println(s"removing entry $key")
    val h = index(keyHashCode(key))
    var e = table(h)
    if (e != endOfList) {
      if (storedKeyEquals(e, key)) {
        //println(s"Found $key at chain $h head")
        table(h) = getNext(e)
        freeEntry(e)
        tableSize = tableSize - 1
        return true
      } else {
        //println(s"searching chain $h")
        var e1 = getNext(e)
        while (e1 != endOfList && !storedKeyEquals(e1, key)) {
          e = e1
          e1 = getNext(e1)
        }
        if (e1 != endOfList) {
          //println(s"Found $key in mid chain $h")
          setNext(e, getNext(e1))
          freeEntry(e1)
          tableSize = tableSize - 1
          return true
        }
      }
    }
    //println(s"entry $key not found")
    false
  }

  /** An iterator returning all entries.
   */
  protected def entriesIterator: Iterator[E] = new Iterator[E] {
    val iterTable = table
    var idx       = lastPopulatedIndex
    var es        = iterTable(idx)

    def hasNext = es != endOfList
    def next() = {
      val res = es
      es = getNext(es)
      while (es == endOfList && idx > 0) {
        idx = idx - 1
        es = iterTable(idx)
      }
      unFlattenEntry(res)
    }
  }
  
  /** Avoid iterator for a 2x faster traversal. */
  protected def foreachEntry[U](f: E => U) {
    val iterTable = table
    var idx       = lastPopulatedIndex
    var es        = iterTable(idx)

    while (es != endOfList) {
      val esNext = getNext(es)    // in case es removed in call to f, in which case its next pointer will point at free list
      f(unFlattenEntry(es))
      es = esNext
      while (es == endOfList && idx > 0) {
        idx -= 1
        es = iterTable(idx)
      }
    }
  }
  
  /** Remove all entries from table
   */
  protected def clearTable() {
    var i = table.length - 1
    while (i >= 0) { table(i) = endOfList; i = i - 1 }
    tableSize = 0
    storage.clear
    highWaterIndex = 0
    firstFreeIndex = endOfList
  }
  
  private def resize(newSize: Int) {
    //println(s"resizing table to $newSize")
    val oldTable = table
    table = Array.fill[Int](newSize)(endOfList) 
    var i = oldTable.length - 1
    while (i >= 0) {
      var e = oldTable(i)
      while (e != endOfList) {
        val h = index(storedHashCode(e))
        val e1 = getNext(e)
        setNext(e, table(h))
        table(h) = e
        e = e1
      }
      i = i - 1
    }
    threshold = newThreshold(_loadFactor, newSize)
  }

  // --- From HashUtils ---

  private final def defaultLoadFactor: Int = 900 // corresponds to 90% (we do want to be compact)
  private final def loadFactorDenum = 1000;
  
  private final def newThreshold(_loadFactor: Int, size: Int) = ((size.toLong * _loadFactor) / loadFactorDenum).toInt

  private final def capacity(expectedSize: Int) = if (expectedSize == 0) 1 else powerOfTwo(expectedSize)
  
  /**
   * Returns a power of two >= `target`.
   */
  private def powerOfTwo(target: Int): Int = {
    /* See http://bits.stephan-brumme.com/roundUpToNextPowerOfTwo.html */
    var c = target - 1;
    c |= c >>>  1;
    c |= c >>>  2;
    c |= c >>>  4;
    c |= c >>>  8;
    c |= c >>> 16;
    c + 1;
  }
  
  protected final def improve(hcode: Int, seed: Int) = {
    // See long comments in HashTable.scala explaining what is going on here 
    val i= scala.util.hashing.byteswap32(hcode)
    val rotation = seed % 32
    val rotated = (i >>> rotation) | (i << (32 - rotation))
    rotated
  }
  
  // Note:
  // we take the most significant bits of the hashcode, not the lower ones
  // this is of crucial importance when populating the table in parallel
  protected final def index(hcode: Int) = {
    val ones = table.length - 1
    val improved = improve(hcode, seedvalue)
    val shifted = (improved >> (32 - java.lang.Integer.bitCount(ones))) & ones
    shifted
  }
}


// The actual Scala collection object. It's a map. That's it.
// Only requirement is that a KeyValueFlattener[A,B] be in scope, for the implicit parameter
// Implemented through a CompactHashTable that uses key type = A and entry type = Pair[A,B]
class FlatteningHashMap[A, B](implicit val kvFlattener:KeyValueFlattener[A,B])
  extends FlatteningHashTable[A, Pair[A,B]]
  with Map[A,B] 
  with MapLike[A, B, FlatteningHashMap[A,B]]
{
  def entryFlattener = kvFlattener
  
  // -- Methods required to implement mutable.MapLike --
 
  def iterator = entriesIterator

  def get(key: A): Option[B] = {
    val e = findEntry(key)
    if (e == endOfList) None
    else Some(entryFlattener.unFlattenValue(storage, indexToData(e)))
  }
 
  def += (kv: (A, B)) = {
    val h = index(keyHashCode(kv._1))
    val e = findEntry0(kv._1, h)
    if (e != endOfList) 
      kvFlattener.flattenValue(kv._2, storage, indexToData(e))
    else
      addEntry0(kv, h)
    this
  }

  def -= (key: A) = { 
    removeEntry(key) 
    this 
  }
    
  // --- Methods here for efficiency ---
  // None of these required to implement mutable.MapLike, but important for efficiency
  
  override def empty = new FlatteningHashMap[A,B]()(entryFlattener)
  override def clear() { clearTable() }
  override def size: Int = tableSize

  // contains and apply overridden to avoid option allocations
  override def contains(key: A): Boolean = findEntry(key) != endOfList

  override def apply(key: A): B = {
    val result = findEntry(key)
    if (result == endOfList) 
      throw new NoSuchElementException
    else entryFlattener.unFlattenValue(storage, indexToData(result))
  }

  override def foreach[C](f: ((A, B)) => C): Unit = foreachEntry(f)  

}



