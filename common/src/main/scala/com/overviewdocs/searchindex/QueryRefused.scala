package com.overviewdocs.searchindex

sealed trait QueryRefused
object QueryRefused {
  case object TooManyClauses extends QueryRefused
}
