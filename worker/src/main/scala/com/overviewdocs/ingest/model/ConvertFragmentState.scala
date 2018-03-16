package com.overviewdocs.ingest.model

/** State of an in-progress conversion, used to handle StepOutputFragments. */
sealed trait ConvertFragmentState
object ConvertFragmentState {
  case object Start extends ConvertFragmentState
  case object End extends ConvertFragmentState
  case class AtChild(child: CreatedFile2) extends ConvertFragmentState
}
