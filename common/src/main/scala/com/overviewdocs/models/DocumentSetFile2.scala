package com.overviewdocs.models

/** Supports many-to-many relation between DocumentSet and File2: specifically,
  * the _tails_ of a File2 tree.
  *
  * One File2 may produce one or more Documents for a DocumentSet. Most often,
  * the user's request is fulfilled by a Document: each Document references the
  * _head_ derived File2 (which includes PDF, Text, and thumbnail). But
  * for some requests (such as Delete DocumentSet and Clone DocumentSet),
  * it makes more sense to start at the _tail_.
  */
case class DocumentSetFile2(
  documentSetId: Long,
  file2Id: Long
)
