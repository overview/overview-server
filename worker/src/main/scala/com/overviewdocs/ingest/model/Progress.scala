package com.overviewdocs.ingest.model

/** A piece of a progress report.
  *
  * Think of the _whole_ job's progress as a continuum along [0.0, 1.0]. Each
  * ProgressPiece here is one piece of the continuum: [min, max]. It will report
  * its progress, from 0.0 to 1.0, by "coloring" a fraction of the slice of
  * continuum it owns. The whole job adds up the colored area to calculate total
  * fraction.
  *
  * Why so complex? Because callers _overwrite_ progress. Conceptually, the
  * whole job needs to keep track of every piece. That can be hairy. So you
  * see? This "coloring" metaphor is actually simple.
  */
class ProgressPiece(
  /** Function that "colors" the whole job's continuum. */
  color: (Double, Double) => Unit,

  /** Left of the area we're given to color (inclusive). */
  min: Double,

  /** Right of the area we're given to color (inclusive). */
  max: Double
) {
  val d = max - min

  /** Colors the appropriate area.
    *
    * @param fraction Fraction of this piece that is complete, in the range
    *                 [0.0, 1.0].
    */
  def report(fraction: Double): Unit = {
    color(min, min + d * fraction)
  }

  /** Splits this coloring area in two: a left and right area.
    *
    * @param fraction Fraction of the area to devote to the left output, in the
    *                 range [0.0, 1.0].
    */
  def bisect(fraction: Double): (ProgressPiece, ProgressPiece) = {
    (
      new ProgressPiece(color, min, min + d * fraction),
      new ProgressPiece(color, min + d * fraction, max)
    )
  }

  /** Gives a slice of the coloring area.
    *
    * @param left Left of this piece's area, in the range [0.0, 1.0].
    * @param right Right of this piece's area, in the range [0.0, 1.0].
    */
  def slice(left: Double, right: Double): ProgressPiece = {
    new ProgressPiece(color, min + d * left, min + d * right)
  }
}

object ProgressPiece {
  val Null = new ProgressPiece((_: Double, _: Double) => (), 0.0, 0.0)
}
