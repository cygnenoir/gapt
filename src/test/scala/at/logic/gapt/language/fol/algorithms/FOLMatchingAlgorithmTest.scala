/*
 * FOLMatchingAlgorithmTest.scala
 *
 */

package at.logic.gapt.language.fol.algorithms

import at.logic.gapt.language.fol._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith( classOf[JUnitRunner] )
class FOLMatchingAlgorithmTest extends SpecificationWithJUnit {
  "FOLMatchingAlgorithm" should {
    val x = FOLVar( "x" )
    val x1 = FOLVar( "x1" )
    val x2 = FOLVar( "x2" )
    val x3 = FOLVar( "x3" )
    val a = FOLConst( "a" )
    val b = FOLConst( "b" )
    val c = FOLConst( "c" )
    val d = FOLConst( "d" )

    "match correctly the lambda expressions f(x, x) and f(a,b)" in {
      val term = FOLFunction( "f", x :: x :: Nil )
      val posInstance = FOLFunction( "f", a :: b :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub must beEqualTo( sub )
    }

    "match correctly the lambda expressions f(x1, x2, c) and f(a,b,c)" in {
      val term = FOLFunction( "f", x1 :: x2 :: c :: Nil )
      val posInstance = FOLFunction( "f", a :: b :: c :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub.get( term ) must beEqualTo( posInstance )
    }

    "not match the lambda expressions f(x1, d, c) and f(a,b,c)" in {
      val term = FOLFunction( "f", x1 :: d :: c :: Nil )
      val posInstance = FOLFunction( "f", a :: b :: c :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub must beEqualTo( None )
    }

    "match the lambda expressions f(x1, x2, c) and f(x1,b,c)" in {
      val term = FOLFunction( "f", x1 :: x2 :: c :: Nil )
      val posInstance = FOLFunction( "f", x1 :: b :: c :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub.get( term ) must beEqualTo( posInstance )
    }

    "not match the lambda expressions f(x1, x2, c, d) and f(x1,b,c)" in {
      val term = FOLFunction( "f", x1 :: x2 :: c :: d :: Nil )
      val posInstance = FOLFunction( "f", x1 :: b :: c :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub must beEqualTo( None )
    }

    "match the lambda expressions f(x1, x2, c) and f(x3,b,c)" in {
      val term = FOLFunction( "f", x1 :: x2 :: c :: Nil )
      val posInstance = FOLFunction( "f", x3 :: b :: c :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub.get( term ) must beEqualTo( posInstance )
    }

    "match the lambda expressions f(x1, x2, x3) and f(x3,b,x3)" in {
      val term = FOLFunction( "f", x1 :: x2 :: x3 :: Nil )
      val posInstance = FOLFunction( "f", x3 :: b :: x3 :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub.get( term ) must beEqualTo( posInstance )
    }

    "match the lambda expressions f(x1, x1, x3) and f(x3,b,g(d))" in {
      val term = FOLFunction( "f", x1 :: x1 :: x3 :: Nil )
      val gd = FOLFunction( "g", d :: Nil )
      val posInstance = FOLFunction( "f", x3 :: b :: gd :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub must beEqualTo( None )
    }

    "match the FOL formulas P(x1,f(x1, g(x1,x3), x3)) and P(c,f(x1, g(x1,a), x3))" in {
      val gx1x3 = FOLFunction( "g", x1 :: x3 :: Nil )
      val gx1a = FOLFunction( "g", x1 :: a :: Nil )
      val term1 = FOLFunction( "f", x1 :: gx1x3 :: x3 :: Nil )
      val term2 = FOLFunction( "f", c :: gx1a :: x3 :: Nil )
      val P1 = FOLAllVar( x1, FOLAtom( "P", x1 :: term1 :: Nil ) )
      val P2 = FOLAllVar( x1, FOLAtom( "P", c :: term2 :: Nil ) )
      val sub1 = FOLMatchingAlgorithm.matchTerms( P1, P2, freeVariables( P2 ) )
      // ??
      0 must beEqualTo( 0 )
    }

    "match the FOL formulas And(P(x1,f(x1, g(x1,x3), x3)),Q(x1)) and And(P(c,f(c, g(x1,a), x3)),Q(c))" in {
      val gx1x3 = FOLFunction( "g", x1 :: x3 :: Nil )
      val gx1a = FOLFunction( "g", x1 :: a :: Nil )
      val term1 = FOLFunction( "f", x1 :: gx1x3 :: x3 :: Nil )
      val term2 = FOLFunction( "f", c :: gx1a :: x3 :: Nil )
      val P1 = FOLAnd( FOLAtom( "P", x1 :: term1 :: Nil ), FOLAtom( "Q", x1 :: Nil ) )
      val P2 = FOLAnd( FOLAtom( "P", c :: term2 :: Nil ), FOLAtom( "Q", c :: Nil ) )
      val sub1 = FOLMatchingAlgorithm.matchTerms( P1, P2, freeVariables( P2 ) )
      sub1 must beEqualTo( None )
    }

    "match the FOL formulas And(P(x2,f(x2, g(x1,x3), x3)),Q(c)) and And(P(c,f(c, g(x1,a), x1)),Q(c))" in {
      val gx2x3 = FOLFunction( "g", x2 :: x3 :: Nil )
      val gax1 = FOLFunction( "g", a :: x1 :: Nil )
      val term1 = FOLFunction( "f", x2 :: gx2x3 :: x3 :: Nil )
      val term2 = FOLFunction( "f", c :: gax1 :: x1 :: Nil )
      val P1 = FOLAnd( FOLAtom( "P", term1 :: Nil ), FOLAtom( "Q", c :: Nil ) )
      val P2 = FOLAnd( FOLAtom( "P", term2 :: Nil ), FOLAtom( "Q", c :: Nil ) )
      val sub1 = FOLMatchingAlgorithm.matchTerms( P1, P2, freeVariables( P2 ) )
      sub1 must beEqualTo( None )
    }

    "match the FOL formulas And(P(f(x2, g(x1,x3), x3)),Q(x2)) and And(P(f(c, g(a,x1), x2)),Q(c))" in {
      val gx2x3 = FOLFunction( "g", x2 :: x3 :: Nil )
      val gcx1 = FOLFunction( "g", c :: x1 :: Nil )
      val term1 = FOLFunction( "f", x2 :: gx2x3 :: x3 :: Nil )
      val term2 = FOLFunction( "f", c :: gcx1 :: x2 :: Nil )
      val P1 = FOLAnd( FOLAtom( "P", term1 :: Nil ), FOLAtom( "Q", x2 :: Nil ) )
      val P2 = FOLAnd( FOLAtom( "P", term2 :: Nil ), FOLAtom( "Q", c :: Nil ) )
      val sub1 = FOLMatchingAlgorithm.matchTerms( P1, P2, freeVariables( P2 ) )
      sub1 must beEqualTo( None )
    }

    "not match the lambda expressions f(x, b) and f(a,b)" in {
      val term = FOLFunction( "f", x :: b :: Nil )
      val posInstance = FOLFunction( "f", a :: b :: Nil )
      val sub = FOLMatchingAlgorithm.matchTerms( term, posInstance, freeVariables( posInstance ) )
      sub.get( term ) must beEqualTo( posInstance )
    }
  }
}

