package at.logic.gapt.utils

import at.logic.gapt.expr.HOLFormula
import at.logic.gapt.proofs.{ HOLSequent, Sequent }
import at.logic.gapt.provers.escargot.Escargot
import at.logic.gapt.provers.groundFreeVariables
import at.logic.gapt.provers.sat.Sat4j
import org.specs2.matcher.OptionMatchers

trait SatMatchers extends OptionMatchers {

  def beUnsat = beNone ^^ { ( f: HOLFormula ) => Sat4j.solve( f ) }

  def beSat =
    beNone ^^ { ( f: HOLFormula ) => Escargot getRobinsonProof groundFreeVariables( f +: Sequent() )._1 } and
      beSome ^^ { ( f: HOLFormula ) => Sat4j.solve( f ) }

  def beValid = beUnsat ^^ { ( f: HOLFormula ) => -f }

  def beValidSequent = beValid ^^ { ( sequent: HOLSequent ) => sequent.toFormula }

}