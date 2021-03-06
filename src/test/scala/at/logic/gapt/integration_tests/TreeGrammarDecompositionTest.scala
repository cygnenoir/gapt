package at.logic.gapt.integration_tests

import at.logic.gapt.proofs.lk.algorithms.cutIntroduction._
import at.logic.gapt.proofs.lk._
import at.logic.gapt.proofs.lk.base.LKProof
import at.logic.gapt.language.fol._
import at.logic.gapt.proofs.algorithms.herbrandExtraction.extractExpansionSequent
import at.logic.gapt.provers.maxsat.{ MaxSat4j, MaxSATSolver }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.SpecificationWithJUnit

/**
 * Created by spoerk on 09.10.14.
 */

@RunWith( classOf[JUnitRunner] )
class TreeGrammarDecompositionTest extends SpecificationWithJUnit {

  private def LinearExampleProof( k: Int, n: Int ): LKProof = {
    val s = "s"
    val c = "0"
    val p = "P"

    val x = FOLVar( "x" )
    val ass = FOLAllVar( x, FOLImp( FOLAtom( p, x :: Nil ), FOLAtom( p, FOLFunction( s, x :: Nil ) :: Nil ) ) )
    if ( k == n ) { // leaf proof {
      val a = FOLAtom( p, Utils.numeral( n ) :: Nil )
      WeakeningLeftRule( Axiom( a :: Nil, a :: Nil ), ass )
    } else {
      val p1 = FOLAtom( p, Utils.numeral( k ) :: Nil )
      val p2 = FOLAtom( p, Utils.numeral( k + 1 ) :: Nil )
      val aux = FOLImp( p1, p2 )
      ContractionLeftRule( ForallLeftRule( ImpLeftRule( Axiom( p1 :: Nil, p1 :: Nil ), LinearExampleProof( k + 1, n ), p1, p2 ), aux, ass, Utils.numeral( k ) ), ass )
    }
  }

  private def toTerms( p: LKProof ): List[FOLTerm] = {
    val testtree = extractExpansionSequent( p, false )
    val testterms = TermsExtraction( testtree )
    val testlanguage = testterms.set
    testlanguage
  }

  private def reconstructLanguage( grammar: Grammar ): List[FOLTerm] = {
    if ( grammar.size > 0 ) {
      val substitutions = grammar.slist.foldRight( List[Set[FOLSubstitution]]() )( ( stuple, acc ) => {
        val evs = stuple._1
        val substitutionSet = stuple._2.map( termVector => FOLSubstitution( evs.zip( termVector ) ) )
        substitutionSet :: acc
      } )
      //println("Substitutions: \n" + substitutions)
      // substitute everything
      substitutions.foldLeft( grammar.u )( ( u, subs ) => {
        u.map( uterm => subs.map( sub => sub( uterm ) ).toList.distinct ).toList.flatten.distinct
      } )
    } else {
      Nil
    }
  }

  "TreeGrammarDecomposition" should {
    "extract and decompose the termset of the linear example proof of 8 (n = 1)" in {
      val proof = LinearExampleProof( 0, 8 )
      val proofLanguage = toTerms( proof )

      val grammar = TreeGrammarDecomposition( proofLanguage, 1, MCSMethod.MaxSAT, new MaxSat4j() )

      // check size
      grammar.get.slist.size shouldEqual 1

      // check validity
      val grammarLanguage = reconstructLanguage( grammar.get )

      proofLanguage diff grammarLanguage must beEmpty
    }

    "extract and decompose the termset of the linear example proof of 18 (n = 2)" in {
      skipped( "this takes too long" )
      val proof = LinearExampleProof( 0, 18 )
      val proofLanguage = toTerms( proof )

      val grammar = TreeGrammarDecomposition( proofLanguage, 2, MCSMethod.MaxSAT, new MaxSat4j() )

      // check size
      grammar.get.slist.size shouldEqual 2

      // check validity
      val grammarLanguage = reconstructLanguage( grammar.get )

      proofLanguage diff grammarLanguage must beEmpty
    }

  }
}

