package org.overviewproject.persistence

class DocumentSetIdGenerator(documentSetId: Long) {

  private def intStream(start: Int): Stream[Int] = start #:: intStream(start + 1)

  private var index: Long = 0
  
  def next: Long = {
    val idHighOrderBits = documentSetId << 32
    index +=1
    
    idHighOrderBits | index
  }
}