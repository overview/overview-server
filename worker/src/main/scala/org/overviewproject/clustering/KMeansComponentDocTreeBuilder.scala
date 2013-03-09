/**
 * KMeansComponentDocTreeBuilder.scala
 * Break documents into connected components that are very similar,
 * then cluster the components using recursive variable k-means
 * 
 * Overview Project, created March 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress, NoProgressReporting }
import org.overviewproject.clustering.ClusterTypes._

