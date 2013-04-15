/**
 * StringTable
 * Memory efficient map between strings and TermIDs
 * Based on the very useful primitive CompactStringToIntMap
 *
 * @author Jonathan Stray
 * Created April 2013
 *
 */


package org.overviewproject.nlp

import org.overviewproject.nlp.DocumentVectorTypes._
import scala.collection.mutable.{ArrayBuffer,HashTable,HashEntry,Map,MapLike}

// CompactStringIntMap Looks like a Map[String,Int]
// But:
//   - we key internally on Array[Char], to avoid all those String objects.
//   - we use our own StringTableEntry object instead of DefaultEntry, to avoid all those wrapped Ints for the values
// Result is about 1/4 the space of HashMap[String,Int]
// This is accomplished with fairly minimal code by deriving from HashTable with a custom Entry object


// Efficient string->int HashTable entry. Store string as char[], keep key and value directly in entry object

// We need to define our own key objects -- really just an Array[Char] -- because Scala's default array hash
// does not return identical values for identical contents. See https://issues.scala-lang.org/browse/SI-1607

class StringTableEntry(val key:String, var value:Int=0) 
  extends HashEntry[String, StringTableEntry]
{
}

// Acts like Map[String,Int] in most ways. 
// It's a real Scala container with all the usual methods (via MapLike), but the companion object does not support all ctors
class CompactStringToIntMap
  extends Map[String,Int]
  with MapLike[String, Int, CompactStringToIntMap]
  with HashTable[String, StringTableEntry] {
  
  type Entry = StringTableEntry
  
  // --- Methods copied verbatim from HashMap.scala ---
  // None of these required to implement mutable.MapLike, but important for efficiency
  override def empty = new CompactStringToIntMap
  override def clear() { clearTable() }
  override def size: Int = tableSize

  // -- Methods required to implement mutable.MapLike --
  // copied from HashMap.scala with minor mods (remove type params)
  def iterator = entriesIterator map {e => (e.key, e.value)}

  def get(key: String): Option[Int] = {
    val e = findEntry(key)
    if (e eq null) None
    else Some(e.value)
  }
  
  def += (kv: (String, Int)): this.type = {
    val e = findOrAddEntry(kv._1, kv._2)
    if (e ne null) e.value = kv._2
    this
  }

  def -=(key: String): this.type = { removeEntry(key); this }

  // --- Methods required to implement HashTable ---
  protected def createNewEntry[B](key: String, value:B): StringTableEntry = {
    new StringTableEntry(key, value.asInstanceOf[Int])
  }
    
  // --- Methods here for efficiency ---
  // Modified from HashMap.scala, as above
  
  // contains and apply overridden to avoid option allocations.
  override def contains(key: String): Boolean = findEntry(key) != null

  override def apply(key: String): Int = {
    val result = findEntry(key)
    if (result eq null) default(key)
    else result.value
  }

  override def foreach[C](f: ((String, Int)) => C): Unit = foreachEntry(e => f(e.key, e.value))  
}

// Ideally, derive from scala.collection.mutable.MutableMapFactory, but we'd need a version not parameterized on key,val types 
object CompactStringToIntMap {
  def apply() = new CompactStringToIntMap()
}


// Now the actual StringTable class. 
// Maintains a bidirectional map between term strings and term IDs
class StringTable {

  private var _stringToId = CompactStringToIntMap()
  private var _idToString = ArrayBuffer[String]()

  def stringToId(term: String): TermID = {
    _stringToId.getOrElseUpdate(term, { _idToString.append(term); _idToString.size - 1 })
  }

  def idToString(id: TermID): String = {
    _idToString(id)
  }
  
  def stringToIdFailIfMissing(term: String) : TermID = {
    _stringToId.getOrElse(term, throw new java.util.NoSuchElementException)
  }
  
  def size = _idToString.size
  
  // translate a string from this table to another. Adds if not in target table.
  def translateIdTo(id: TermID, s:StringTable) : TermID = {
    val term = idToString(id)
    s.stringToId(term)
  }
  
  def mkString() = _idToString.toString
}
