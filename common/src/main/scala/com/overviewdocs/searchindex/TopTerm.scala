package com.overviewdocs.searchindex

case class TopTerm(
  term: String,
  frequency: Long,
  nDocuments: Int,
)
