package com.overviewdocs.csv

import scala.collection.mutable

/** Parses CSV data.
  *
  * I know, I know, there are lots of others out there. This one is magical
  * because it's non-blocking. It achieves that by avoiding Java's I/O
  * libraries.
  *
  * Usage:
  *
  *     val parser = new CsvParser
  *     parser.write("id,name\n1,foo".toCharArray)
  *     parser.getParsedRows // Iterable(Array("id", "name"))
  *     parser.clearParsedRows
  *     parser.write("\n".toCharArray)
  *     parser.getParsedRows // Iterable(Array("1", "foo"))
  *     parser.write("2,bar")
  *     parser.endWrite // writes a final "\n"
  *     parser.getParsedRows // Iterable(Array("1", "foo"), Array("2", "bar"))
  *
  * Features:
  *
  * * Folds `\r`, `\n`, `\r\n` all to `\n`
  * * Parses quotes mid-value: for instance, `1,foo" bar" ,2` becomes
  *   `Array("1", "foo bar ", "2")`. (This accepts invalid CSVs.)
  * * Handles `"` quotes: `foo,"bar,\n",baz`
  * * Handles escaped quotes-within-quotes as `""`: `"foo""bar"` becomes
  *   `foo"bar`.
  */
class CsvParser {
  private val DQuote = '"'
  private val Comma = ','
  private val Cr = '\r'
  private val Lf = '\n'

  private val parsedRows = mutable.Buffer[Array[String]]()
  private val currentRow = mutable.Buffer[String]()
  private val currentValue = mutable.Buffer[Char]()

  private sealed trait State
  private case object RowStart extends State
  private case object Normal extends State
  private case object InQuote extends State
  private case object AfterQuote extends State

  private var state: State = RowStart

  private def finishRow: Unit = {
    if (currentRow.length > 0) {
      parsedRows.+=(currentRow.toArray)
    }
    currentRow.clear
  }

  private def finishValue: Unit = {
    currentRow.+=(new String(currentValue.toArray))
    currentValue.clear
  }

  /** Adds more input characters. */
  def write(input: Array[Char], offset: Int, length: Int): Unit = {
    val endOffset = offset + length

    for (i <- offset until endOffset) {
      val c: Char = input(i)

      c match {
        case Cr | Lf => state match {
          case RowStart => {}                // empty row; ignore
          case InQuote => currentValue.+=(c) // just a normal character
          case Normal | AfterQuote => {      // end of a record
            finishValue
            finishRow
            state = RowStart
          }
        }
        case Comma => state match {
          case InQuote => currentValue.+=(c)
          case Normal => finishValue
          case AfterQuote | RowStart => {
            finishValue
            state = Normal
          }
        }
        case DQuote => state match {
          case InQuote => state = AfterQuote
          case AfterQuote => {
            currentValue.+=(DQuote)
            state = InQuote
          }
          case RowStart | Normal => {
            // Yes, we make Normal go to InQuote ... otherwise, we'd detect
            // errors in the input file. We assume our users would prefer to
            // ignore errors.
            state = InQuote
          }
        }
        case _ => state match {
          case InQuote | Normal => currentValue.+=(c)
          case AfterQuote => {
            // Clearly an error: the only thing after a quote should be another
            // quote, a newline, or a comma. But we choose to ignore errors.
            currentValue.+=(c)
            state = Normal
          }
          case RowStart => {
            currentValue.+=(c)
            state = Normal
          }
        }
      }
    }
  }

  /** Adds more input characters. */
  def write(input: Array[Char]): Unit = write(input, 0, input.length)

  /** Indicates there is no more input.
    *
    * This is important: otherwise, the parser might not see the last row.
    */
  def end: Unit = write(Array('\n'))

  /** Returns all fully-parsed rows.
    *
    * If the parser is mid-row, the partially-parsed row will be omitted.
    *
    * To save memory, you should clear these rows after you're done with them,
    * with clearParsedRows.
    */
  def getParsedRows: Seq[Array[String]] = parsedRows.toSeq

  /** Clears the memory used by the fully-parsed rows.
    */
  def clearParsedRows: Unit = parsedRows.clear

  /** Returns `true` iff we have no half-parsed rows.
    */
  def isFullyParsed: Boolean = state == RowStart
}
