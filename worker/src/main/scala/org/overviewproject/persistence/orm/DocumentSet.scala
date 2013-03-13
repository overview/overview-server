package org.overviewproject.persistence.orm

import org.squeryl.KeyedEntity


// Can only be used to read and update DocumentSets
// since many default values are missing
case class DocumentSet(
    documentCount: Int,
    documentProcessingErrorCount: Int,
    importOverflowCount: Int,
    id: Long = 0l
    ) extends KeyedEntity[Long] 
