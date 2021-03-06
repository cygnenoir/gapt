/*
 * SkolemizationTest.scala
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package at.logic.gapt.proofs.algorithms.skolemization

import at.logic.gapt.proofs.lk.base.{ beSyntacticMultisetEqual, LKProof, Sequent }

import at.logic.gapt.language.hol._
import at.logic.gapt.proofs.lk._
import skolemize._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import at.logic.gapt.language.lambda.symbols.StringSymbol
import at.logic.gapt.language.lambda.types._

@RunWith( classOf[JUnitRunner] )
class SkolemizationTest extends SpecificationWithJUnit {

  sequential
  "Skolemization" should {
    val x = HOLVar( "x", Ti )
    val y = HOLVar( "y", Ti )
    val f = HOLAllVar( x, HOLAtom( HOLConst( "P", Ti -> To ), x :: Nil ) )
    val s0 = StringSymbol( "s_{0}" )
    val s1 = StringSymbol( "s_{2}" )
    val s2 = StringSymbol( "s_{4}" )
    val s3 = StringSymbol( "s_{6}" )
    SkolemSymbolFactory.reset
    val stream = SkolemSymbolFactory.getSkolemSymbols
    val p = HOLConst( "P", Ti -> To )
    val r = HOLConst( "R", Ti -> ( Ti -> To ) )

    "leave a formula with only weak quantifiers untouched" in {
      skolemize( f, 1, stream ) must beEqualTo( f )
    }

    "introduce correctly a Skolem constant" in {
      val skfun = HOLConst( stream.head, Ti )
      val skf = HOLAtom( p, skfun :: Nil )
      skolemize( f, 0, stream ) must beEqualTo( skf )
    }

    "handle a binary formula correctly" in {
      val y = HOLVar( StringSymbol( "y" ), Ti )
      val rxy = HOLAtom( r, x :: y :: Nil )
      val f2 = HOLImp( f, HOLAllVar( x, HOLExVar( y, rxy ) ) )

      val skfun0 = HOLConst( stream.head, Ti )
      val skfun1 = HOLFunction( HOLConst( stream.tail.head, Ti -> Ti ), x :: Nil )
      val skf1 = HOLAtom( p, skfun0 :: Nil )
      val skf2 = HOLAtom( r, x :: skfun1 :: Nil )

      val skf = HOLImp( skf1, HOLAllVar( x, skf2 ) )
      skolemize( f2, 1, stream ) must beEqualTo( skf )

      // now we skolemize the skolemize formula, with opposite polarity
      val skfun2 = HOLConst( stream.tail.tail.head, Ti )
      val skfun3 = HOLFunction( HOLConst( stream.tail.head, Ti -> Ti ), skfun2 :: Nil )
      val skf3 = HOLAtom( r, skfun2 :: skfun3 :: Nil )
      val skf4 = HOLImp( skf1, skf3 )
      skolemize( skolemize( f2, 1, stream ), 0, stream.tail ) must beEqualTo( skf4 )
    }

    "handle a simple proof correctly" in {
      val s5 = StringSymbol( "s_{5}" )
      val cs5 = HOLConst( s5, Ti )
      val alpha = HOLVar( StringSymbol( "α" ), Ti )
      val Palpha = HOLAtom( p, alpha :: Nil )
      val Ps0 = HOLAtom( p, cs5 :: Nil )
      val allxPx = HOLAllVar( x, HOLAtom( p, x :: Nil ) )
      val ax = Axiom( Palpha :: Nil, Palpha :: Nil )
      val proof = ForallRightRule(
        ForallLeftRule( ax,
          Palpha, allxPx, alpha ),
        Palpha, allxPx, alpha )

      val ax_sk = Axiom( Ps0 :: Nil, Ps0 :: Nil )
      val proof_sk = ForallLeftRule( ax_sk, Ps0, allxPx, cs5 )

      val res = skolemize( proof )
      res.root.toFSequent must beEqualTo( proof_sk.root.toFSequent )
    }

    "work for a cut-free proof (1)" in {
      /* Proof is:
                 R(a,b) :- R(a,b)
                 ----------------------   Ex,r
                 R(a,b) :- ex y. R(a,y)
            ---------------------------   Ex,l
            ex y.R(a,y) :- ex y. R(a,y)
      ---------------------------------- All,l
      all x.ex y.R(x,y) :- ex y. R(a,y)
      --------------------------------------- All,r
      all x.ex y.R(x,y) :- all x.ex y. R(x,y)

      Skolemized: (wrote s0 instead of s_{0} for easier reading)
          R(s1,s0(s1)) :- R(s1,s0(s1))
          ----------------------------   Ex,r
          R(s1,s0(s1)) :- ex y. R(s1,y)
      ---------------------------------- All,l
      all x.R(x,s0(x)) :- ex y. R(s1,y)
       */

      val a = HOLVar( "a", Ti )
      val b = HOLVar( "b", Ti )
      val Rab = HOLAtom( r, a :: b :: Nil )
      val exyRay = HOLExVar( y, HOLAtom( r, a :: y :: Nil ) )
      val allxexyRxy = HOLAllVar( x, HOLExVar( y, HOLAtom( r, x :: y :: Nil ) ) )
      val ax = Axiom( Rab :: Nil, Rab :: Nil )
      val r1 = ExistsRightRule( ax, ax.root.succedent( 0 ), exyRay, b )
      val r2 = ExistsLeftRule( r1, r1.root.antecedent( 0 ), exyRay, b )
      val r3 = ForallLeftRule( r2, r2.root.antecedent( 0 ), allxexyRxy, a )
      val proof: LKProof = ForallRightRule( r3, exyRay, allxexyRxy, a )

      val fs0 = HOLConst( StringSymbol( "s_{0}" ), Ti -> Ti )
      val s1c = HOLConst( StringSymbol( "s_{2}" ), Ti )
      val s0s1 = HOLApp( fs0, s1c )
      val sR = HOLAtom( r, List( s1c, s0s1 ) )
      val sax = Axiom( List( sR ), List( sR ) )

      val exyRs1y = HOLExVar( y, HOLAtom( r, List( s1c, y ) ) )
      //      val exyRs1s0s1 = ExVar( y, Atom( r, List(a,y) ) )
      val sr1 = ExistsRightRule( sax, sax.root.succedent( 0 ), exyRs1y, s0s1 )

      val allxRxs0x = HOLAllVar( x, HOLAtom( r, List( x, HOLApp( fs0, x ) ) ) )
      val sr2 = ForallLeftRule( sr1, sr1.root.antecedent( 0 ), allxRxs0x, s1c )
      val proof_sk = sr2

      SkolemSymbolFactory.reset

      //println("=== starting skolemization ===")
      val skolemized_proof: LKProof = skolemize( proof )
      //println("=== endsequent skolemized ===")
      //println(skolemized_proof.root)
      //println(proof_sk.root)

      skolemized_proof.root must beSyntacticMultisetEqual( proof_sk.root )
    }

  }
}
