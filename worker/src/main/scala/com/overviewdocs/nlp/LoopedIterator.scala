package com.overviewdocs.nlp

/** Loops infinitely over an underlying (presumably finite) iterator.
  *
  * Resets when it hits the end by calling makeIter. Can be empty if makeIter
  * returns empty iter.
  */
class LoopedIterator[T](makeIter: => Iterator[T]) extends Iterator[T] {

  private var current = makeIter
  private val trulyEmpty = current.isEmpty

  def hasNext = !trulyEmpty
  def next : T = {
    if (!current.hasNext && !trulyEmpty) {
      current = makeIter
    }
    current.next
  }
}
