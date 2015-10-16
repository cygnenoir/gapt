package at.logic.gapt.proofs.ceres

import at.logic.gapt.expr.HOLFormula
import at.logic.gapt.proofs.lkNew.LKProof

/**
 * Created by marty on 10/27/15.
 */
object extractStruct {
  def apply( p: LKProof ): Struct[HOLFormula] =
    StructCreators.extract[HOLFormula]( p )
  def extract[Data]( p: LKProof, predicate: HOLFormula => Boolean ): Struct[HOLFormula] =
    StructCreators.extract[HOLFormula]( p, predicate )

}