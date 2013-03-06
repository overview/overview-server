package org.overviewproject.persistence

import org.specs2.mutable.Specification


class DocumentSetIdGeneratorSpec extends Specification {

  "DocumentIdGenerator" should {
    
    "generate ids composed of the document set id and an index" in {
      val id: Long = 1
      val idGenerator = new DocumentSetIdGenerator(id)
      val highOrderBits: Long = 1l << 32
      val expectedIds = Seq.tabulate(10)(n => highOrderBits | (n + 1))
      
      val ids = Seq.fill(10)(idGenerator.next)
      ids must be equalTo(expectedIds)
    }
    
  }
}