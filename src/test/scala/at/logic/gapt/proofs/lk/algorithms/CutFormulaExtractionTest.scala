package at.logic.gapt.proofs.lk.algorithms

import at.logic.gapt.language.hol._
import at.logic.gapt.language.lambda.types._
import at.logic.gapt.proofs.lk.base.{ BinaryLKProof, Sequent }
import at.logic.gapt.proofs.lk.{ Axiom, CutRule }
import at.logic.gapt.proofs.occurrences.FormulaOccurrence
import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.runner.JUnitRunner

@RunWith( classOf[JUnitRunner] )
class CutFormulaExtractionTest extends SpecificationWithJUnit {
  "Substitutions" should {
    val x = HOLVar( "x", Ti )
    val P = HOLConst( "P", Ti -> To )
    val px = HOLAtom( P, List( x ) )
    val ax1 = Axiom( List( px ), List( px ) )
    val ax2 = Axiom( List( px ), List( px ) )
    val proof = CutRule( ax1, ax2, ax1.root.succedent.toList.head, ax2.root.antecedent.toList.head )

    val ax1_ = Axiom( List( px ), List( px ) )
    val ax2_ = Axiom( List( px ), List( px ) )
    val proof_ = CutRule( ax1_, ax2_, ax1_.root.succedent.toList.head, ax2_.root.antecedent.toList.head )

    def toSequent( aux: FormulaOccurrence ) = Sequent( Nil, List( aux ) )

    "apply correctly to a proof with one cut" in {
      val cutproofs = getCutsAsProofs( proof )
      val cutformulas = cutformulaExtraction( proof )
      cutproofs must beEqualTo( List( proof ) )
    }

    "apply correctly to a proof with three cuts" in {
      val proof2 = CutRule( proof, proof_, proof.root.succedent.head, proof_.root.antecedent.head )
      val prooflist: List[BinaryLKProof] = List( proof, proof_, proof2 )

      val cutproofs = getCutsAsProofs( proof2 )
      val cutformulas = cutformulaExtraction( proof2 )

      cutproofs must beEqualTo( prooflist )
    }

  }
}
