package com.overviewdocs.ingest.models

import play.api.libs.json.JsObject

/** File2: we know it has been Processed and _not_ Ingested.
  *
  * This holds the information we need to start or resume ingesting.
  *
  * When we resume ingesting, some children may be ingested while other children
  * may not. We'll be able to load these uningested ProcessedFile2s with one
  * SELECT.
  */
case class ProcessedFile2(
  id: Long,
  documentSetId: Long,
  parentId: Option[Long],
  nChildren: Int,
  nIngestedChildren: Int
) {
  def areChildrenIngested: Boolean = nChildren == nIngestedChildren
}
