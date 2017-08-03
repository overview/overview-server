/*
 * CsvImportSource.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package com.overviewdocs.csv

import scala.collection.{immutable,mutable}

import com.overviewdocs.util.{Configuration,Textify}

/** Accepts parsed CSV data as input; handles the header specially, then
  * translates each row into a CsvDocument.
  *
  * Usage:
  *
  *   val producer = new CsvDocumentProducer
  *   producer.addCsvRow(Array("id", "text"))
  *   producer.addCsvRow(Array("1", "foo"))
  *   producer.getProducedDocuments   // immutable.Seq(CsvDocument("1", "foo", ...))
  *   producer.clearProducedDocuments // reclaim memory
  */
class CsvDocumentProducer {
  private val producedDocuments = mutable.Buffer[CsvDocument]()

  private var maybeHeader: Option[CsvHeader] = None

  def getProducedDocuments: immutable.Seq[CsvDocument] = producedDocuments.toIndexedSeq
  def clearProducedDocuments: Unit = producedDocuments.clear
  def metadataColumnNames: immutable.Seq[String] = maybeHeader.toIndexedSeq.flatMap(_.metadataColumnNames)

  /** Handles another CSV row. */
  def addCsvRow(csvRow: Array[String]): Unit = {
    val cleanRow: Array[String] = csvRow.map(Textify.apply _)

    maybeHeader match {
      case None => maybeHeader = Some(new CsvHeader(cleanRow))
      case Some(header) => producedDocuments.+=(header.createDocumentFromRow(cleanRow))
    }
  }

  private class CsvHeader(columnNames: Array[String]) {
    private val MaxNMetadataColumns = 100 // crazy-high limit to prevent DoS
    private val asMap = columnNames.map(_.toLowerCase).zipWithIndex.toMap

    val textIndex: Option[Int] = asMap.get("text").orElse(asMap.get("contents")).orElse(asMap.get("snippet"))
    val idIndex: Option[Int] = asMap.get("id")
    val urlIndex: Option[Int] = asMap.get("url")
    val titleIndex: Option[Int] = asMap.get("title")
    val tagsIndex: Option[Int] = asMap.get("tags")

    private val nonMetadataIndices: immutable.Seq[Int] = Vector(textIndex, idIndex, urlIndex, titleIndex, tagsIndex).flatten

    /** The indices of metadata columns.
      *
      * Normally, every CSV column that is specified in the header is a
      * metadata column. But we have exceptions:
      *
      * * If there are too many metadata columns, we truncate the list (to
      *   prevent DoS).
      * * If two metadata columns share the same name, we only parse the first.
      * * If a metadata column is named `""`, we nix it.
      */
    private val metadataIndices: Array[Int] = (columnNames.indices.toSet -- nonMetadataIndices)
      .toArray
      .take(MaxNMetadataColumns).sorted // sort after truncate, to avoid DoS
      .reverseMap(index => columnNames(index) -> index) // Array[String,Int]
      .toMap // Map[String,Int], with earlier columns overriding others of same name
      .-("")
      .values.toArray.sorted

    val metadataColumnNames = metadataIndices.map(columnNames)

    private def fetch(array: Array[String], maybeIndex: Option[Int]): String = {
      maybeIndex match {
        case Some(index) => if (array.length > index) array(index) else ""
        case None => ""
      }
    }

    def createDocumentFromRow(row: Array[String]): CsvDocument = CsvDocument(
      fetch(row, idIndex),
      fetch(row, urlIndex),
      fetch(row, titleIndex),
      fetch(row, tagsIndex).split(",").map(_.trim).filterNot(_.isEmpty).toSet,
      Textify.truncateToNChars(fetch(row, textIndex), CsvDocumentProducer.MaxNCharsPerDocument),
      metadataIndices.map(index => columnNames(index) -> fetch(row, Some(index)))
    )
  }
}

object CsvDocumentProducer {
  private val MaxNCharsPerDocument = Configuration.getInt("max_n_chars_per_document")
}
