package com.overviewdocs.models

/** A page of search results from DocumentCloud.
  */
case class DocumentCloudImportIdList(
  id: Int,
  documentCloudImportId: Int,

  /** 0-based page of search results. */
  pageNumber: Int,

  /** IDs from DocumentCloud: our to-fetch list, encoded as a String.
    *
    * It contains:
    *
    * * `documentCloudId`
    * * `title`
    * * `nPages`
    * * `fullTextUrl`
    * * `pageTextUrlTemplate` (replace `{page}` with page number)
    *
    * See com.overviewdocs.documentcloud.IdList for encoding logic.
    */
  idsString: String,

  /** The number of DocumentCloud documents in this list.
    *
    * We cache this so we can avoid a RAM hit when summing.
    */
  nDocuments: Int,

  /** The number of DocumentCloud pages in this list.
    *
    * We cache this so we can avoid a RAM hit when summing.
    */
  nPages: Int
)

object DocumentCloudImportIdList {
  case class CreateAttributes(
    documentCloudImportId: Int,
    pageNumber: Int,
    idsString: String,
    nDocuments: Int,
    nPages: Int
  )
}
