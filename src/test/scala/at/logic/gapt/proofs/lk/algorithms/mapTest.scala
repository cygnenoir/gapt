package at.logic.gapt.proofs.lk.algorithms

import at.logic.gapt.language.fol.FOLFormula
import at.logic.gapt.language.fol.algorithms.convertHolToFol
import at.logic.gapt.language.hol._
import at.logic.gapt.language.lambda.types._
import at.logic.gapt.proofs.lk._
import at.logic.gapt.proofs.lk.base._
import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.runner.JUnitRunner

/**
 * Created by marty on 10/17/14.
 */
@RunWith( classOf[JUnitRunner] )
class mapTest extends SpecificationWithJUnit {
  "map" should {
    val List( u, x, y, z ) = List( "u", "x", "y", "z" ) map ( HOLVar( _, Ti ) )
    val List( a, b, c ) = List( "a", "b", "c" ) map ( HOLConst( _, Ti ) )
    val List( p ) = List( "p" ) map ( HOLConst( _, Ti -> ( Ti -> To ) ) )
    val List( q ) = List( "q" ) map ( HOLConst( _, Ti -> To ) )
    val pxy = HOLAtom( p, List( x, y ) )
    val qz = HOLAtom( q, List( z ) )

    val deMorgan1 = HOLImp( HOLNeg( HOLAnd( pxy, qz ) ),
      HOLOr( HOLNeg( pxy ), HOLNeg( qz ) ) )
    val deMorgan2 = HOLImp( HOLOr( HOLNeg( pxy ), HOLNeg( qz ) ),
      HOLNeg( HOLAnd( pxy, qz ) ) )

    val deMorgan = HOLAnd( deMorgan1, deMorgan2 )
    val Some( p1 ) = solve.solvePropositional( FSequent( Nil, List( deMorgan ) ), true, true )

    "be able to convert a hol proof to a fol proof" in {
      val ( proof, _ ) = map_proof( p1, convertHolToFol.apply )
      for ( f <- proof.root.toFSequent.formulas ) {
        val r = if ( f.isInstanceOf[FOLFormula] ) "" else ( f.toString + " is not a fol formula" )
        "" must_== ( r )
      }

      ok
    }

    "be able to convert a proof with a quantified cut" in {
      val sub = HOLSubstitution( z, a )
      val sub2 = HOLSubstitution( z, u )
      val zdeMorgan = HOLAllVar( u, sub2( deMorgan ) )
      val zdeMorgan1 = HOLAllVar( u, sub2( deMorgan1 ) )
      val Some( proof ) = solve.solvePropositional( FSequent( List( deMorgan ), List( deMorgan1 ) ) )

      val i1 = ForallLeftRule( proof, proof.root.antecedent( 0 ), zdeMorgan, z )
      val i1a = ForallRightRule( i1, i1.root.succedent( 0 ), zdeMorgan1, z )

      val Some( proof2 ) = solve.solvePropositional( FSequent( sub( deMorgan1 ) :: Nil, sub( deMorgan1 ) :: Nil ) )
      val i2 = ForallLeftRule( proof2, proof2.root.antecedent( 0 ), zdeMorgan1, a )

      val cut = CutRule( i1a, i2, i1a.root.succedent( 0 ), i2.root.antecedent( 0 ) )

      def fun( e: HOLExpression ): HOLExpression = e match {
        case f: HOLFormula => convertHolToFol.convertFormula( f )
        case _             => convertHolToFol.convertTerm( e )
      }

      val ( cutproof, _ ) = map_proof( cut, fun )
      for ( f <- cutproof.root.toFSequent.formulas ) {
        val r = if ( f.isInstanceOf[FOLFormula] ) "" else ( f.toString + " is not a fol formula" )
        "" must_== ( r )
      }
      ok
    }

  }

}
