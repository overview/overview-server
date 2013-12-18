package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity

case class LogEntry(
    val id: Long = 0L,
    val documentSetId: Long,
    val userId: Long,
    val date: Timestamp,
    val component: String,
    val action: String = "",
    val details: String = ""
    ) extends KeyedEntity[Long] 
