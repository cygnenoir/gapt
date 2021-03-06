/*
 * SimpleHOLParser.scala
 *
 */

package at.logic.gapt.proofs.shlk.algorithms

import java.io.InputStreamReader

import at.logic.gapt.formats.shlk_parsing.SHLK
import at.logic.gapt.language.lambda.types.To
import at.logic.gapt.language.schema._
import at.logic.gapt.proofs.lk._
import org.junit.runner.RunWith
import org.specs2.execute.Success
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith( classOf[JUnitRunner] )
class SimpleSLKParserTest extends SpecificationWithJUnit {
  "SimpleSLKParser" should {

    sequential
    "parse correctly a SLK-proof" in {
      val var3 = SchemaAtom( SchemaVar( "x3", To ), Nil )
      val var4 = SchemaAtom( SchemaVar( "x4", To ), Nil )
      val ax1 = Axiom( var3 :: Nil, var3 :: Nil )
      val ax2 = Axiom( var4 :: Nil, var4 :: Nil )
      val negl = NegLeftRule( ax1, var3 )
      val proof = OrLeftRule( negl, ax2, var3, var4 )

      val A0 = IndexedPredicate( "A", IntZero() )
      val i = IntVar( "i" )
      val Ai2 = IndexedPredicate( "A", Succ( Succ( i ) ) )
      val Ai = IndexedPredicate( "A", Succ( i ) )
      val f1 = SchemaAnd( A0, BigAnd( i, Ai, IntZero(), Succ( i ) ) )
      val ax11 = Axiom( A0 :: Nil, A0 :: Nil )

      val s = new InputStreamReader( getClass.getClassLoader.getResourceAsStream( "shlk-adder.lks" ) )

      val map = SHLK.parseProof( s )

      Success()

    }
  }
}
