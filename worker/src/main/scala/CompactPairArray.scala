/**
 * CompactPairArray.scala
 *
 * An object that (mostly) looks like an ArrayBuffer[Pair[A,B]], but stores internally as two parallel arrays.
 * This can drastically improve the memory efficiency of storing large numbers of pairs of primitives.
 *
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.util


import scala.collection.mutable.{IndexedSeq, IndexedSeqOptimized, Builder}
import scala.collection.generic.{SeqFactory, CanBuildFrom}


class CompactPairArray[A : ClassManifest,B : ClassManifest] 
    extends IndexedSeq[Pair[A,B]] 
    with IndexedSeqOptimized[Pair[A,B], CompactPairArray[A,B]] 
    with Builder[Pair[A,B], CompactPairArray[A,B]] 
{
  // Internal state: two Arrays, plus a size
  var aArray = new Array[A](0)
  var bArray = new Array[B](0)
  var numStoredElements = 0
  
  def aSeq : IndexedSeq[A] = aArray.view.take(numStoredElements)
  def bSeq : IndexedSeq[B] = bArray.view.take(numStoredElements)
  
  def availableSize = aArray.size
   
  // Set the size of the backing store. Throws away elements if newSize < numStoredElements
  protected def resizeStorage(newSize:Int) : Unit = {
    if (newSize != availableSize) {
      var aTemp = new Array[A](newSize)
      scala.compat.Platform.arraycopy(aArray, 0, aTemp, 0, numStoredElements)
      aArray = aTemp
      var bTemp = new Array[B](newSize)
      scala.compat.Platform.arraycopy(bArray, 0, bTemp, 0, numStoredElements)
      bArray = bTemp
      numStoredElements = math.min(numStoredElements, newSize)
    }
  }
  
  protected def ensureSize(neededSize:Int) : Unit = {
    if (neededSize > availableSize) {
      if (numStoredElements == 0) {
        resizeStorage(neededSize)
      } else {
        var newSize = numStoredElements        
        while (newSize < neededSize) newSize *= 2  //increase by factor of two, as usual
        resizeStorage(newSize)        
      }
    }
  }
  
  // --- SeqLike methods ---

  def length() = {
    numStoredElements
  }
  
  def apply(idx:Int) : Pair[A,B] = {
    Pair[A,B](aArray(idx), bArray(idx))
  }
  
  def update(idx: Int, elem: Pair[A,B]) : Unit = {
    aArray(idx) = elem._1
    bArray(idx) = elem._2
  }

//  def seq = iterator

  // We are our own builder
  override protected def newBuilder = {
    new CompactPairArray[A,B]
  }
  
  // --- Builder methods ---
  def +=(elem : Pair[A,B])  = {
    ensureSize(numStoredElements + 1)
    aArray(numStoredElements) = elem._1
    bArray(numStoredElements) = elem._2
    numStoredElements += 1
    this
  }
  
  def clear() = {
    aArray = new Array[A](0)
    bArray = new Array[B](0)
    numStoredElements = 0
    this
  }
  
  // when someone calls result, we trim our storage
  def result() = {
    resizeStorage(numStoredElements)
    this
  }
  
  override def sizeHint(size:Int) : Unit = {
    resizeStorage(math.max(size,numStoredElements))
  }
  
  import CompactPairArray.canBuildFrom    // bring into scope so we get the right conversions for map etc.
}

// Companion object provides nice ctor syntax 
object CompactPairArray {
 
  // trait CanBuildFrom[-From, -Elem, +To]
 
  implicit def canBuildFrom[A,B] : CanBuildFrom[CompactPairArray[A,B], Pair[A,B], CompactPairArray[A,B]] = 
    new CanBuildFrom[CompactPairArray[A, B], Pair[A,B], CompactPairArray[A, B]] {
    
      // typical case: just forward to the object we're building from
      def apply(from: CompactPairArray[A,B]): Builder[Pair[A,B], CompactPairArray[A, B]] = {
         from.newBuilder
      }

      // I'm not sure if this no-argument version will ever be called in practice. The only call I know
      // is in PriorityQueue.dequeueAll, but then we wouldn't be building from a CompactPairArray
      // Also somewhat complex to implement due to type erasure, since CPA needs to know the types of A,B
      // to create its array, and we don't have the appropriate manifests here. 
      // Nonetheless, implement by creating a CompactPairArray[Any,Any], which should work --jms 11/27/12
      def apply(): Builder[Pair[A,B], CompactPairArray[A,B]] = {
        new CompactPairArray[A,B]()(
            Manifest.Any.asInstanceOf[ClassManifest[A]],
            Manifest.Any.asInstanceOf[ClassManifest[B]])
      }          
  }
  
  // Construct from a sequence: var c = CompactPairArray((1,2.0), (3,4.0), ... ) 
  // Really, we need to figure out how to derive from SeqFactory and we'll get this and more (e.g. Fill, Tabulate)
  def apply[A:ClassManifest,B:ClassManifest](v : Pair[A,B]*) = {
    var cpa = new CompactPairArray[A,B]
    cpa.sizeHint(v.size)
    v.foreach(cpa += _)
    cpa
  }
}

