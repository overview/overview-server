package com.overviewdocs.models

/** Information about the "Full" document (as opposed to a "partial" document).
  *
  * A "Partial" document is a Document with a pageNumber. The full-document
  * data is populated using a separate SQL query, hence this separate object.
  */
case class FullDocumentInfo(
  /** This "partial" document's page number. */
  pageNumber: Int,
  /** Number of pages in the "full" document. */
  nPages: Int,
  /** File2 ID of the "full" document. */
  fullDocumentFile2Id: Long
);
