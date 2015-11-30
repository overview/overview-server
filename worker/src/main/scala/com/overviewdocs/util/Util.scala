/**
 * Utils.scala
 *
 * Logging singleton, ActorSystem singleton, progress reporting classes
 *
 * Overview, created August 2012
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.util

import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.language.implicitConversions  // for toMutableSet
import scala.util.control.Exception._

// Iterator that loops infinitely over an underlying (presumably finite) iterator
// Resets when it hits the end by calling makeIter. Can be empty if makeIter returns empty iter.
class LoopedIterator[T](makeIter: => Iterator[T]) extends Iterator[T] {

  private var current = makeIter
  private val trulyEmpty = current.isEmpty

  def hasNext = !trulyEmpty
  def next : T = {
    if (!current.hasNext && !trulyEmpty)
      current = makeIter
    current.next
  }
}


// An implicit that adds toMutableSet to any Iterable (toSet almost always returns immutable set)
class ToMutableSet[T](val container:Iterable[T]) {
  def toMutableSet() : scala.collection.mutable.Set[T] = {
    val s = scala.collection.mutable.Set[T]()
    container foreach { s += _ }
    s
  }
}
object ToMutableSet {
  implicit def augmentIterable[T](x: Iterable[T]) : ToMutableSet[T] = new ToMutableSet(x)
}

// Some basic utility functions for dealing with ranges
object Ranges {
  def clip(a:Int, x:Int, b:Int) = Math.min(b, Math.max(a, x))
  def clip(a:Long, x:Long, b:Long) = Math.min(b, Math.max(a, x))
  def clip(a:Float, x:Float, b:Float) = Math.min(b, Math.max(a, x))
  def clip(a:Double, x:Double, b:Double) = Math.min(b, Math.max(a, x))
}

// Trait that knows how to compose an error key

/**
 * Encode state and error names into keys that can be internationalized by the client.
 * keyName must match a key on in conf/messages under views.DocumentSet._documentSet.job_state_description.
 * Optional params are pasted together with ":" and can then referred to as {0}, {1} etc. in messages
 */
trait TranslatableMessage {
  val keyName:String
  val params:Seq[String]

  override def toString = (keyName +: params) mkString(":")
}

// Base class for errors that the worker can produce, each of which has a translatable message
class DisplayedError(val keyName:String, val params:String*) extends Exception with TranslatableMessage  {
}
