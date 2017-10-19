
package at.logic.gapt.examples.tip.prod

import at.logic.gapt.formats.tip.TipProblemDefinition

object def_prop_07 extends TipProblemDefinition {

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
      TBase( "list" ),
      List(
        TipConstructor( hoc"'nil' :list", List() ),
        TipConstructor( hoc"'cons' :sk>list>list", List( hoc"'head' :list>sk", hoc"'tail' :list>list" ) ) ) ),
    TipDatatype(
      TBase( "Nat" ),
      List(
        TipConstructor( hoc"'Z' :Nat", List() ),
        TipConstructor( hoc"'S' :Nat>Nat", List( hoc"'p' :Nat>Nat" ) ) ) ) )

  override def uninterpretedConsts = List(
    hoc"'S' :Nat>Nat",
    hoc"'nil' :list",
    hoc"'cons' :sk>list>list",
    hoc"'p' :Nat>Nat",
    hoc"'tail' :list>list",
    hoc"'Z' :Nat",
    hoc"'head' :list>sk" )

  override def assumptions = List()

  override def functions = List(
    TipFun(
      hoc"'qrev' :list>list>list",
      List(
        hof"(qrev(nil:list, y:list): list) = y",
        hof"(qrev(cons(z:sk, xs:list): list, y:list): list) = qrev(xs, cons(z, y))" ) ),
    TipFun(
      hoc"'plus' :Nat>Nat>Nat",
      List(
        hof"(plus(#c(Z: Nat), y:Nat): Nat) = y",
        hof"(plus(S(z:Nat): Nat, y:Nat): Nat) = S(plus(z, y))" ) ),
    TipFun(
      hoc"'length' :list>Nat",
      List(
        hof"(length(nil:list): Nat) = #c(Z: Nat)",
        hof"(length(cons(y:sk, xs:list): list): Nat) = S(length(xs))" ) ) )

  override def goal = hof"∀x ∀y (length(qrev(x:list, y:list): list): Nat) = plus(length(x), length(y))"
}

