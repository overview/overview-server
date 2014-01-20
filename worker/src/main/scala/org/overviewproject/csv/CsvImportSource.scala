/*
 * CsvImportSource.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.Reader
import scala.annotation.tailrec
import scala.collection.Iterable
import au.com.bytecode.opencsv.CSVReader

/**
  * Takes a Reader attached to a stream representing CSV data and provides
  * an iterator that returns each row as a CsvImportDocument.
  * The first row must have header labels for the columns. One column
  * must be labelled text. If a column is labelled id, its values will
  * be stored in the CsvImportDocument.suppliedId field.
  *
  * Arguments:
  * * textify: see org.overviewproject.util.Textify
  * * reader: a Reader
  */
class CsvImportSource(textify: (String) => String, reader: Reader) extends Iterable[CsvImportDocument] {
  private val TextColumn: String = "text"
  private val SuppliedIdColumn: String = "id"
  private val UrlColumn: String = "url"
  private val TitleColumn: String = "title"

  /**
   * An iterator that returns CsvImportDocuments, based on the header information
   * in the first row of the input.
   */
  val iterator = new Iterator[CsvImportDocument] {
    private val Separator = ','
    private val Quote = '\"'
    private val NoEscape = '\0' // Setting escape char to \0 disables escaping

    private val csvParser = new CSVReader(reader, Separator, Quote, NoEscape)
    private var nextLine = csvParser.readNext() // read ahead to be able to evaluate hasNext

    // Column headers with index
    private val columns: Map[String, Int] = readHeaders
    private def findTextColumn: Option[Int] = 
      Seq(TextColumn, "contents", "snippet").flatMap(columns.get(_)).headOption
      
      
    def hasNext: Boolean = nextLine != null

    /**
     *  @return the next valid CsvImportDocument. Throws and exception if
     *  no column has the text header. Rows with not enough columns to have
     *  a text column are ignored.
     */
    def next(): CsvImportDocument = {
      require(findTextColumn.isDefined)

      readRow match {
        case null => null
        case c => new CsvImportDocument(text(c), suppliedId(c), url(c), title(c), tags(c))
      }
    }

    // Return text if the column exists, "" otherwise.
    private def text(row: Array[String]): String = {
      val textIndex = findTextColumn.get
      if (row.length > textIndex) row(textIndex)
      else ""
    }

    // Return user supplied id value if found.
    private def suppliedId(row: Array[String]): Option[String] = getOptColumn(row, SuppliedIdColumn)

    // return the url if url column exists
    private def url(row: Array[String]): Option[String] = getOptColumn(row, UrlColumn)

    // return the title if title column exists
    private def title(row: Array[String]): Option[String] = getOptColumn(row, TitleColumn)
    
    // return a list of tag names
    private def tags(row: Array[String]): Iterable[String] =  getOptColumn(row, "tags") match {
      case Some(tags) => tags.split(",")
      case None => Array.empty[String]
    }
    
    
      
    
    // if the columnName was defined in the header row, @return the value in the column, else None
    private def getOptColumn(row: Array[String], columnName: String): Option[String] =
      columns.get(columnName).flatMap(c =>
        if (row.size > c && !row(c).isEmpty) Some(row(c))
        else None)

    // Read ahead and return current row
    private def readRow: Array[String] = {
      val row = nextLine
      Option(row).map(_.transform(textify))

      nextLine = csvParser.readNext()

      row
    }

    // Convert a row to a map of header -> columnIndex
    private def readHeaders: Map[String, Int] = {
      val headerRow = readRow
      headerRow.map(_.trim.toLowerCase).zipWithIndex.toMap
    }
  }

}
