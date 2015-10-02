import at.logic.gapt.examples.UniformAssociativity3ExampleProof
import at.logic.gapt.expr.fol.{Numeral, FOLSubstitution}
import at.logic.gapt.expr._
import at.logic.gapt.expr.hol.univclosure
import at.logic.gapt.grammars.{minimizeSipGrammar, normalFormsSipGrammar, minimizeRecursionScheme, SipRecSchem}
import at.logic.gapt.proofs.lk.LKToExpansionProof
import at.logic.gapt.proofs.{Suc, Sequent, Ant}
import at.logic.gapt.proofs.expansionTrees.{InstanceTermEncoding, toShallow, ExpansionSequent}
import at.logic.gapt.provers.maxsat.bestAvailableMaxSatSolver
import at.logic.gapt.provers.prover9.Prover9Prover
import at.logic.gapt.formats.prover9.Prover9TermParserLadrStyle._
import at.logic.gapt.provers.veriT.VeriTProver
import at.logic.gapt.utils.time

val p9 = new Prover9Prover
def removeEqAxioms(eseq: ExpansionSequent) =
  eseq.zipWithIndex filter {
    case (et, Ant(_)) => !p9.isValid(toShallow(et))
    case (et, Suc(_)) => !p9.isValid(Neg(toShallow(et)))
  } map {
    _._1
  }
val endSequent = Sequent(
  Seq("s(x+y) = x+s(y)", "x+0 = x")
    map (s => univclosure(parseFormula(s))),
  Seq(parseFormula("(x+x)+x = x+(x+x)")))
val instanceProofs =
  (0 until 6).map { n => n -> removeEqAxioms(LKToExpansionProof(UniformAssociativity3ExampleProof(n))) }

val termEncoding = InstanceTermEncoding(endSequent)
var instanceLanguages = instanceProofs map {
  case (n, expSeq) =>
    n -> termEncoding.encode(expSeq)
}

// Ground the instance languages.
instanceLanguages = instanceLanguages map {
  case (n, lang) =>
    val groundingSubst = FOLSubstitution(freeVariables(lang).
      map { c => FOLVar(c.name) -> FOLConst(c.name) }.toSeq)
    n -> lang.map(groundingSubst.apply)
}

val nfRecSchem = SipRecSchem.normalForms(instanceLanguages)
println(nfRecSchem.rules.size)
val minimized = time {
  minimizeRecursionScheme(nfRecSchem, SipRecSchem.toTargets(instanceLanguages), SipRecSchem.targetFilter, bestAvailableMaxSatSolver)
}
println(minimized)
println

(0 until 10) foreach { i =>
  val instanceLang = minimized.parametricLanguage(Numeral(i))
  val instanceSeq = FOLSubstitution(FOLVar("x") -> Numeral(i))(termEncoding.decodeToFSequent(instanceLang map {
    _.asInstanceOf[FOLTerm]
  }))
  val isCovered = instanceLanguages.find(_._1 == i).map(_._2.toSet subsetOf instanceLang)
  val isTaut = new VeriTProver().isValid(instanceSeq)
  println(s"$i: tautology=$isTaut covers=$isCovered")
}

val sipG = SipRecSchem.toSipGrammar(minimized)
println(sipG)

val nfSipG = normalFormsSipGrammar(instanceLanguages)
println(nfSipG.productions.size)
println(time {
  minimizeSipGrammar(nfSipG, instanceLanguages, bestAvailableMaxSatSolver)
})