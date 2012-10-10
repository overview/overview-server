package helpers

import models.PersistentTagInfo
import models.core.DocumentIdList


case class TestTag(id: Long, name: String, color: Option[String], documentIds: DocumentIdList) extends PersistentTagInfo
    
  
