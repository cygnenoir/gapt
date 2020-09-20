/**
 * Utility functions for first-order logic.
 */

package gapt.expr.formula.fol

import gapt.expr._
import gapt.expr.formula.All
import gapt.expr.formula.And
import gapt.expr.formula.Bottom
import gapt.expr.formula.Eq
import gapt.expr.formula.Ex
import gapt.expr.formula.Formula
import gapt.expr.formula.Imp
import gapt.expr.formula.Neg
import gapt.expr.formula.Or
import gapt.expr.formula.Top
import gapt.expr.formula.hol.containsQuantifier
import gapt.expr.ty.Ti
import gapt.expr.util.subTerms
import gapt.proofs.HOLSequent
import gapt.proofs.context.Context
import gapt.proofs.context.facet.Constants

import scala.collection.mutable

object isFOLFunction {
  /** Returns whether t is a function. */
  def apply( t: FOLTerm ): Boolean = apply( t, _ => true )

  /** Returns whether t is a function whose name fulfills to a given condition. */
  def apply( t: FOLTerm, cond: String => Boolean ): Boolean = t match {
    case FOLFunction( f, _ ) => cond( f.toString )
    case _                   => false
  }
}

/** Unsafely extracts the function name from a term. Fails if the term is not a function. */
object FOLFunctionName {
  def apply( t: FOLTerm ): String = t match { case FOLFunction( f, _ ) => f }
}

/** Unsafely extracts the function arguments from a term. Fails if the term is not a function. */
object FOLFunctionArgs {
  def apply( t: FOLTerm ): List[FOLTerm] = t match { case FOLFunction( _, a ) => a }
}

/**
 * Flat subterms are obtained by decomposing a sequence of applications in one
 * step i.e. the flat subterms of f x₁ ... xₙ are the term itself and the
 * variables x₁,...,xₙ.
 */
object flatSubterms {

  /**
   * Retrieves "flat" subterms occurrences.
   * @param t The term whose flat subterms are to be retrieved.
   * @return All the flat subterms of `t`.
   */
  def apply( t: Expr ): Set[Expr] = apply( Some( t ) )

  /**
   * Retrieves "flat" subterm occurrences.
   * @param expressions The expressions whose flat subterms are to be retrieved.
   * @return All flat subterm occurrences of the given expressions.
   */
  def apply( expressions: Iterable[Expr] ): Set[Expr] = {

    val subTerms = mutable.Set[Expr]()

    def getFlatSubterms( expr: Expr ): Unit = {
      if ( !subTerms.contains( expr ) ) {
        subTerms += expr
        val Apps( _, as ) = expr
        as.foreach { getFlatSubterms }
      }
    }

    expressions.foreach { getFlatSubterms }
    subTerms.toSet
  }

  /**
   * @see [[gapt.expr.formula.fol.flatSubterms]].
   */
  def apply( t: FOLTerm ): Set[FOLTerm] = apply( Some( t ) )

  /**
   * @see [[gapt.expr.formula.fol.flatSubterms]].
   */
  def apply( language: Iterable[FOLTerm] )( implicit dummyImplicit: DummyImplicit ): Set[FOLTerm] =
    apply( language: Iterable[Expr] ).asInstanceOf[Set[FOLTerm]]
}

object folTermSize {
  def apply( t: Expr ): Int =
    t match { case Apps( hd, as ) => 1 + apply( as ) }

  def apply( ts: Iterable[Expr] ): Int =
    ts.view.map( apply ).sum
}

object Numeral {
  def apply( k: Int ): FOLTerm = k match {
    case 0 => FOLFunction( "0" )
    case _ => FOLFunction( "s", Numeral( k - 1 ) )
  }

  def unapply( t: FOLTerm ): Option[Int] = t match {
    case FOLFunction( "s", List( Numeral( k ) ) ) => Some( k + 1 )
    case FOLFunction( "0", List() )               => Some( 0 )
    case _                                        => None
  }
}

object isFOLPrenexSigma1 {
  def apply( f: Expr ): Boolean = f match {
    case Ex.Block( _, matrix: FOLFormula ) if !containsQuantifier( matrix ) => true
    case _ => false
  }

  def apply( seq: HOLSequent ): Boolean =
    seq.antecedent.forall( isFOLPrenexPi1( _ ) ) && seq.succedent.forall( isFOLPrenexSigma1( _ ) )
}

object isPrenexSigma1 {
  def apply( f: Expr ): Boolean = f match {
    case Ex.Block( _, matrix ) if !containsQuantifier( matrix ) => true
    case _ => false
  }

  def apply( seq: HOLSequent ): Boolean =
    seq.antecedent.forall( isPrenexPi1( _ ) ) && seq.succedent.forall( isPrenexSigma1( _ ) )
}

object isFOLPrenexPi1 {
  def apply( f: Expr ): Boolean = f match {
    case All.Block( _, matrix: FOLFormula ) if !containsQuantifier( matrix ) => true
    case _ => false
  }
}

object isPrenexPi1 {
  def apply( f: Expr ): Boolean = f match {
    case All.Block( _, matrix ) if !containsQuantifier( matrix ) => true
    case _ => false
  }
}

object Utils {
  // Constructs the FOLTerm f^k(a)
  def iterateTerm( a: FOLTerm, f: String, k: Int ): FOLTerm =
    if ( k < 0 ) throw new Exception( "iterateTerm called with negative iteration count" )
    else if ( k == 0 ) a
    else FOLFunction( f, iterateTerm( a, f, k - 1 ) :: Nil )

  // Constructs the FOLTerm s^k(0)
  def numeral( k: Int ) = Numeral( k )
}

object getArityOfConstants {
  /**
   * Get the constants and their arity from a given formula
   * @param t the FOL expression from which we want to extract
   * @return a set of pairs (arity, name)
   */
  def apply( t: FOLExpression ): Set[( Int, String )] = t match {
    case FOLConst( s )          => Set( ( 0, s ) )
    case FOLVar( _ )            => Set[( Int, String )]()
    case FOLAtom( h, args )     => Set( ( args.length, h.toString ) ) ++ args.map( arg => getArityOfConstants( arg ) ).flatten
    case FOLFunction( h, args ) => Set( ( args.length, h.toString ) ) ++ args.map( arg => getArityOfConstants( arg ) ).flatten

    case And( x, y )            => getArityOfConstants( x ) ++ getArityOfConstants( y )
    case Eq( x, y )             => getArityOfConstants( x ) ++ getArityOfConstants( y )
    case Or( x, y )             => getArityOfConstants( x ) ++ getArityOfConstants( y )
    case Imp( x, y )            => getArityOfConstants( x ) ++ getArityOfConstants( y )
    case Neg( x )               => getArityOfConstants( x )
    case Ex( x, f )             => getArityOfConstants( f )
    case All( x, f )            => getArityOfConstants( f )
  }
}

object QuantifierStructure {
  object Exists {
    /**
     * Matches ∃ₖ formulas.
     * @param formula The formula to be matched.
     * @return k if `formula` is ∃ₖ.
     */
    def unapply( formula: FOLFormula ): Option[Int] =
      if ( isQuantifierFree( formula ) ) {
        Some( 0 )
      } else {
        formula match {
          case Ex.Block( _ :: _, Forall( k ) ) => Some( k + 1 )
          case _                               => None
        }
      }
  }
  object Forall {
    /**
     * Matches ∀ₖ formulas.
     * @param formula The formula to be matched.
     * @return k if `formula` is ∀ₖ.
     */
    def unapply( formula: FOLFormula ): Option[Int] =
      if ( isQuantifierFree( formula ) ) {
        Some( 0 )
      } else {
        formula match {
          case All.Block( _ :: _, Exists( k ) ) => Some( k + 1 )
          case _                                => None
        }
      }
  }
  private def isQuantifierFree( formula: FOLFormula ): Boolean =
    !subTerms( formula ).exists {
      case All( _, _ ) | Ex( _, _ ) => true
      case _                        => false
    }
}

object thresholds {

  object exactly {

    /**
     * @param fs The input formulas A1, ..., An.
     * @return A formula that is logically equivalent to -A1 & ... & -An.
     */
    def noneOf( fs: Seq[Formula] ): Formula = -Or( fs )

    /**
     * @param fs The input formulas A₁, ..., Aₙ.
     * @return A formula that is logically equivalent to
     *         (A₁ ∨ ... ∨ Aₙ) ∧ ( ∧(1 ≤ i < j ≤ n) ¬Aᵢ ∨ ¬Aⱼ ).
     */
    def oneOf( fs: Seq[Formula] ): Formula = fs match {
      case Seq()    => Bottom()
      case Seq( f ) => f
      case _ =>
        val ( a, b ) = fs.splitAt( fs.size / 2 )
        ( noneOf( a ) & oneOf( b ) ) | ( oneOf( a ) & noneOf( b ) )
    }

  }

  object atMost {

    /**
     * @param fs The input formulas A₁, ..., Aₙ.
     * @return A formula that is logically equivalent to ( ∧(1 ≤ i < j ≤ n) ¬Aᵢ ∨ ¬Aⱼ ).
     */
    def oneOf( fs: Seq[Formula] ): Formula = fs match {
      case Seq() | Seq( _ ) => Top()
      case _ =>
        val ( a, b ) = fs.splitAt( fs.size / 2 )
        ( exactly.noneOf( a ) & atMost.oneOf( b ) ) | ( atMost.oneOf( a ) & exactly.noneOf( b ) )
    }

  }

}

object naive {

  object exactly {

    /**
     * @param fs The input formulas A1, ..., An.
     * @return A formula that is logically equivalent to -A1 & ... & -An.
     */
    def noneOf( fs: Seq[Formula] ): Formula = -Or( fs )

    /**
     * @param fs The input formulas A₁, ..., Aₙ.
     * @return A formula that is logically equivalent to
     *         (A₁ ∨ ... ∨ Aₙ) ∧ ( ∧(1 ≤ i < j ≤ n) ¬Aᵢ ∨ ¬Aⱼ ).
     */
    def oneOf( fs: Seq[Formula] ): Formula = Or( fs ) & atMost.oneOf( fs )

  }

  object atMost {

    /**
     * @param fs The input formulas A₁, ..., Aₙ.
     * @return A formula that is logically equivalent to ( ∧(1 ≤ i < j ≤ n) ¬Aᵢ ∨ ¬Aⱼ ).
     */
    def oneOf( fs: Seq[Formula] ): Formula = And( for ( a <- fs; b <- fs if a != b ) yield -a | -b )

  }

}

object natMaker {
  def apply( i: Int, thevar: Expr = Const( "0", Ti ) )( implicit ctx: Context ): Expr = {
    val suc = ctx.get[Constants].constants.getOrElse( "s", { throw new Exception( "nat not defined" ) } )
    if ( i > 0 ) Apps( suc, Seq( natMaker( i - 1, thevar ) ) )
    else if ( thevar.equals( Const( "0", Ti ) ) ) ctx.get[Constants].constants.getOrElse( "0", { throw new Exception( "nat not defined" ) } )
    else thevar
  }
}