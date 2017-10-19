
package at.logic.gapt.examples.tip.isaplanner

import at.logic.gapt.formats.tip.TipProblemDefinition

object def_prop_17 extends TipProblemDefinition {

  import at.logic.gapt.expr._
  import at.logic.gapt.formats.tip.{ TipDatatype, TipFun, TipConstructor }

  override def sorts = List(
    TBase( "sk" ) )

  override def datatypes = List(
    TipDatatype(
      TBase( "o" ),
      List(
        TipConstructor( hoc"'⊤' :o", List() ),
        TipConstructor( hoc"'⊥' :o", List() ) ) ),
    TipDatatype(
      TBase( "Nat" ),
      List(
        TipConstructor( hoc"'Z' :Nat", List() ),
        TipConstructor( hoc"'S' :Nat>Nat", List( hoc"'p' :Nat>Nat" ) ) ) ) )

  override def uninterpretedConsts = List(
    hoc"'S' :Nat>Nat",
    hoc"'p' :Nat>Nat",
    hoc"'Z' :Nat" )

  override def assumptions = List()

  override def functions = List(
    TipFun(
      hoc"'le' :Nat>Nat>o",
      List(
        hof"le(#c(Z: Nat), y:Nat): o",
        hof"¬le(S(z:Nat): Nat, #c(Z: Nat))",
        hof"(le(S(z:Nat): Nat, S(x2)) ⊃ le(z, x2)) ∧ (le(z, x2) ⊃ le(S(z), S(x2)))" ) ),
    TipFun(
      hoc"'equal' :Nat>Nat>o",
      List(
        hof"equal(#c(Z: Nat), #c(Z: Nat)): o",
        hof"¬equal(#c(Z: Nat), S(z:Nat): Nat)",
        hof"¬equal(S(x2:Nat): Nat, #c(Z: Nat))",
        hof"(equal(S(x2:Nat): Nat, S(y2)) ⊃ equal(x2, y2)) ∧   (equal(x2, y2) ⊃ equal(S(x2), S(y2)))" ) ) )

  override def goal = hof"∀n   ((le(n:Nat, #c(Z: Nat)) ⊃ equal(n, #c(Z: Nat))) ∧     (equal(n, #c(Z: Nat)) ⊃ le(n, #c(Z: Nat))))"
}

