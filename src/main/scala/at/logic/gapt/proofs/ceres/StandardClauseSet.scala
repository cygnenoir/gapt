/*
 * StandardClauseSet.scala
 *
 */

package at.logic.gapt.proofs.ceres.clauseSets

import at.logic.gapt.proofs.{ HOLClause, HOLSequent }
import at.logic.gapt.expr._
import at.logic.gapt.utils.logging.Logger
import scala.annotation.tailrec
import scala.util.control.TailCalls._
import at.logic.gapt.proofs.ceres._

/**
 * This implements the clause set transformation of the original CERES method. Specialized extractors exist for
 * CERES_omega and schematic CERES.
 */
object SimpleStandardClauseSet extends AlternativeStandardClauseSet( ( x, y ) => ( x, y ) )

/**
 * The idea here is that we use subsumption during clause set generation
 * But take care, this clause set generation is incomplete!
 * Take e.g. S1 = :- F(x) < :-F(a) and S2 = :- G(x) < :- G(b) but
 * S1 x S2 = :- F(x), G(x) does not subsume :- F(a), G(b).
 * TODO: make a safe version (e.g. disjoint variables are safe)
 */
object AlternativeStandardClauseSet extends AlternativeStandardClauseSet(
  ( set1, set2 ) => {
    val set1_ = set1.filterNot( s1 => set2.exists( s2 => StillmanSubsumptionAlgorithmHOL.subsumes( s2, s1 ) ) )
    val set2_ = set2.filterNot( s2 => set1_.exists( s1 => StillmanSubsumptionAlgorithmHOL.subsumes( s1, s2 ) ) )
    //println("Set1: "+set1.size+" - "+(set1.size-set1_.size))
    //println("Set2: "+set2.size+" - "+(set2.size-set2_.size))
    ( set1_, set2_ )
  }
)

/**
 * Should calculate the same clause set as [[StandardClauseSet]], but without the intermediate representation of a
 * normalized struct. Does not work for Schema, for CERESomega only if all labels are empty (clauses are correct,
 * but labels forgotten).
 */
class AlternativeStandardClauseSet[Data]( val optimize_plus: ( Set[HOLSequent], Set[HOLSequent] ) => ( Set[HOLSequent], Set[HOLSequent] ) ) {
  def apply( struct: Struct[Data] ): Set[HOLSequent] = struct match {
    case A( fo, _ )                     => Set( HOLSequent( Nil, List( fo ) ) )
    case Dual( A( fo, _ ) )             => Set( HOLSequent( List( fo ), Nil ) )
    case EmptyPlusJunction()            => Set()
    case EmptyTimesJunction()           => Set( HOLSequent( Nil, Nil ) )
    case Plus( EmptyPlusJunction(), x ) => apply( x )
    case Plus( x, EmptyPlusJunction() ) => apply( x )
    case Plus( A( f1, _ ), Dual( A( f2, _ ) ) ) if f1 == f2 =>
      Set()
    case Plus( Dual( A( f2, _ ) ), A( f1, _ ) ) if f1 == f2 =>
      Set()
    case Plus( x, y ) =>
      val ( x_, y_ ) = optimize_plus( apply( x ), apply( y ) )
      x_ ++ y_
    case Times( EmptyTimesJunction(), x, _ ) => apply( x )
    case Times( x, EmptyTimesJunction(), _ ) => apply( x )
    case Times( x, y, _ ) =>
      val xs = apply( x )
      val ys = apply( y )
      xs.flatMap( x1 => ys.flatMap( y1 => Set( delta_compose( x1, y1 ) ) ) )
    case _ => throw new Exception( "Unhandled case: " + struct )
  }

  /* Like compose, but does not duplicate common terms */
  private def delta_compose( fs1: HOLSequent, fs2: HOLSequent ) = HOLSequent(
    fs1.antecedent ++ fs2.antecedent.diff( fs1.antecedent ),
    fs1.succedent ++ fs2.succedent.diff( fs1.succedent )
  )
}

/**
 * This implements the standard clause set from Bruno's thesis. It has a computational drawback: we create the normalized
 * struct first, which is later on converted to a clause set. The normalized struct easily becomes so big that recursive
 * functions run out of stack. The [[AlternativeStandardClauseSet]] performs a direct conversion, which can handle bigger
 * sizes.
 */
object StandardClauseSet extends Logger {
  override def loggerName = "CeresLogger"

  def normalize[Data]( struct: Struct[Data] ): Struct[Data] = struct match {
    case s: A[Data]           => s
    case s: Dual[Data]        => s
    case EmptyTimesJunction() => struct
    case EmptyPlusJunction()  => struct
    case Plus( s1, s2 )       => Plus( normalize( s1 ), normalize( s2 ) )
    case Times( s1, s2, aux ) => merge( normalize( s1 ), normalize( s2 ) )
  }

  def transformStructToClauseSet[Data]( struct: Struct[Data] ): List[HOLClause] = clausify( normalize( struct ) )

  @tailrec
  def transformCartesianProductToStruct[Data](
    cp:  List[Tuple2[Struct[Data], Struct[Data]]],
    acc: List[Struct[Data] => Struct[Data]]
  ): Struct[Data] = cp match {
    case ( i, j ) :: Nil =>
      acc.reverse.foldLeft[Struct[Data]]( Times( i, j, Nil ) )( ( struct, fun ) => fun( struct ) )
    case ( i, j ) :: rest =>
      val rec: Struct[Data] => Struct[Data] = { x => Plus( Times( i, j, Nil ), x ) }
      transformCartesianProductToStruct( rest, rec :: acc )

    case Nil =>
      acc.reverse.foldLeft[Struct[Data]]( EmptyPlusJunction() )( ( struct, fun ) => fun( struct ) )
  }

  private def merge[Data]( s1: Struct[Data], s2: Struct[Data] ): Struct[Data] = {
    trace( "merge on sizes " + s1.size + " and " + s2.size )
    val ( list1, list2 ) = ( getTimesJunctions( s1 ), getTimesJunctions( s2 ) )
    val cartesianProduct = for ( i <- list1; j <- list2 ) yield ( i, j )
    trace( "done: " + s1.size + " and " + s2.size )
    transformCartesianProductToStruct( cartesianProduct, Nil )
  }

  /**
   * *
   * This is the optimized version of [[slowgetTimesJunctions]] in continuation passing style.
   * @param struct the input struct
   * @return a flattened version of the tree withtimes and junctions
   */
  private def getTimesJunctions[Data]( struct: Struct[Data] ): List[Struct[Data]] =
    getTimesJunctions( struct, ( x: List[Struct[Data]] ) => done( x ) ).result

  /**
   * This is the optimized version of [[slowgetTimesJunctions]] in continuation passing style.
   * @param struct the input struct
   * @param fun the continuation
   * @return a tailrec object representing the result
   */
  private def getTimesJunctions[Data]( struct: Struct[Data], fun: List[Struct[Data]] => TailRec[List[Struct[Data]]] ): TailRec[List[Struct[Data]]] = struct match {
    case Times( _, _, _ )     => fun( List( struct ) )
    case EmptyTimesJunction() => fun( List( struct ) )
    case A( _, _ )            => fun( List( struct ) )
    case Dual( _ )            => fun( List( struct ) )
    case EmptyPlusJunction()  => fun( Nil )
    case Plus( s1, s2 ) => tailcall( getTimesJunctions( s1, ( x: List[Struct[Data]] ) =>
      tailcall( getTimesJunctions( s2, ( y: List[Struct[Data]] ) => fun( x ::: y ) ) ) ) )
  }

  private def slowgetTimesJunctions[Data]( struct: Struct[Data] ): List[Struct[Data]] = struct match {
    case Times( _, _, _ )     => struct :: Nil
    case EmptyTimesJunction() => struct :: Nil
    case A( _, _ )            => struct :: Nil
    case Dual( _ )            => struct :: Nil
    case EmptyPlusJunction()  => Nil
    case Plus( s1, s2 )       => slowgetTimesJunctions( s1 ) ::: slowgetTimesJunctions( s2 )
  }

  private def getLiterals[Data]( struct: Struct[Data] ): List[Struct[Data]] = getLiterals( struct, ( x: List[Struct[Data]] ) => done( x ) ).result
  private def getLiterals[Data]( struct: Struct[Data], fun: List[Struct[Data]] => TailRec[List[Struct[Data]]] ): TailRec[List[Struct[Data]]] = struct match {
    case s: A[Data]           => fun( s :: Nil )
    case s: Dual[Data]        => fun( s :: Nil )
    case EmptyTimesJunction() => fun( Nil )
    case EmptyPlusJunction()  => fun( Nil )
    case Plus( s1, s2 ) => tailcall( getLiterals( s1, ( x: List[Struct[Data]] ) =>
      tailcall( getLiterals( s2, ( y: List[Struct[Data]] ) => fun( x ::: y ) ) ) ) )
    case Times( s1, s2, _ ) => tailcall( getLiterals[Data]( s1, ( x: List[Struct[Data]] ) =>
      tailcall( getLiterals( s2, ( y: List[Struct[Data]] ) => fun( x ::: y ) ) ) ) )
  }

  private def slowgetLiterals[Data]( struct: Struct[Data] ): List[Struct[Data]] = struct match {
    case s: A[Data]           => s :: Nil
    case s: Dual[Data]        => s :: Nil
    case EmptyTimesJunction() => Nil
    case EmptyPlusJunction()  => Nil
    case Plus( s1, s2 )       => slowgetLiterals( s1 ) ::: slowgetLiterals( s2 )
    case Times( s1, s2, _ )   => slowgetLiterals( s1 ) ::: slowgetLiterals( s2 )
  }

  private def clausifyTimesJunctions[Data]( struct: Struct[Data] ): HOLClause = {
    val literals = getLiterals( struct )
    val ( negative, positive ) =
      literals.foldLeft( List[HOLFormula](), List[HOLFormula]() )( ( pair, s ) => {
        val ( ns, ps ) = pair
        s match {
          case Dual( A( f, _ ) ) => ( f :: ns, ps )
          case A( f, _ )         => ( ns, f :: ps )
        }
      } )

    HOLClause( negative, positive )
  }

  def clausify[Data]( struct: Struct[Data] ): List[HOLClause] = {
    val timesJunctions = getTimesJunctions( struct )
    timesJunctions.map( x => clausifyTimesJunctions( x ) )
  }
}

object SimplifyStruct {
  def apply[Data]( s: Struct[Data] ): Struct[Data] = s match {
    case EmptyPlusJunction()                 => s
    case EmptyTimesJunction()                => s
    case A( _, _ )                           => s
    case Dual( EmptyPlusJunction() )         => EmptyTimesJunction()
    case Dual( EmptyTimesJunction() )        => EmptyPlusJunction()
    case Dual( x )                           => Dual( SimplifyStruct( x ) )
    case Times( x, EmptyTimesJunction(), _ ) => SimplifyStruct( x )
    case Times( EmptyTimesJunction(), x, _ ) => SimplifyStruct( x )
    case Times( x, Dual( y: Struct[Data] ), aux ) if x.formula_equal( y ) =>
      //println("tautology deleted")
      EmptyPlusJunction()
    case Times( Dual( x: Struct[Data] ), y, aux ) if x.formula_equal( y ) =>
      //println("tautology deleted")
      EmptyPlusJunction()
    case Times( x, y, aux ) =>
      //TODO: adjust aux formulas, they are not needed for the css construction, so we can drop them,
      // but this method should be as general as possible
      Times( SimplifyStruct( x ), SimplifyStruct( y ), aux )
    case PlusN( terms ) =>
      //println("Checking pluses of "+terms)
      assert( terms.nonEmpty, "Implementation Error: PlusN always unapplies to at least one struct!" )
      val nonrendundant_terms = terms.foldLeft[List[Struct[Data]]]( Nil )( ( x, term ) => {
        val simple = SimplifyStruct( term )
        if ( x.filter( _.formula_equal( simple ) ).nonEmpty )
          x
        else
          simple :: x
      } )
      /*
      val saved = nonrendundant_terms.size - terms.size
      if (saved >0)
        println("Removed "+ saved + " terms from the struct!")
        */
      PlusN( nonrendundant_terms.reverse )

  }
}

