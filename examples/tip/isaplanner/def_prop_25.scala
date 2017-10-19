
package at.logic.gapt.examples.tip.isaplanner

import at.logic.gapt.formats.tip.TipProblemDefinition

object def_prop_25 extends TipProblemDefinition {

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
      hoc"'max2' :Nat>Nat>Nat",
      List(
        hof"(max2(#c(Z: Nat), y:Nat): Nat) = y",
        hof"(max2(S(z:Nat): Nat, #c(Z: Nat)): Nat) = S(z)",
        hof"(max2(S(z:Nat): Nat, S(x2)): Nat) = S(max2(z, x2))" ) ),
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

  override def goal = hof"∀a   ∀b   ((equal(max2(a:Nat, b:Nat): Nat, b) ⊃ le(a, b)) ∧     (le(a, b) ⊃ equal(max2(a, b), b)))"
}

