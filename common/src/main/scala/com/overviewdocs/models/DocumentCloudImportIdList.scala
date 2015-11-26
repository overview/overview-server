package com.overviewdocs.models

/** A page of search results from DocumentCloud.
  */
case class DocumentCloudImportIdList(
  id: Int,
  documentCloudImportId: Int,

  /** 0-based page of search results. */
  pageNumber: Int,

  /** IDs from DocumentCloud: our to-fetch list.
    *
    * CSV format: "123-foo-bar,1\n234-bar-baz,2". First column is ID from
    * DocumentCloud; second column is number of pages.
    */
  idsString: String
) {
}

object DocumentCloudImportIdList {
  case class CreateAttributes(
    documentCloudImportId: Int,
    pageNumber: Int,
    idsString: String
  )
}
