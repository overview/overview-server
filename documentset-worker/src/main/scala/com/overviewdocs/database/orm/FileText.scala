package com.overviewdocs.database.orm

import com.overviewdocs.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class FileText(
    fileId: Long,
    text: String) extends KeyedEntity[CompositeKey2[Long, String]] {
  
    override def id = compositeKey(fileId, text)
}