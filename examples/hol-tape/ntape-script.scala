/* *************************************************************************** *
   n-Tape Proof script: loads the n-Tape proof and creates a proof in atomic    
   cut normal form by applying the CERES_omega method.                          
   Usage:                                                                       
     (start cli in gapt/source directory or adjust the filename variable below command)     
     :load examples/hol-tape/ntape-script.scala                               
     prooftool(acnf)                                                            
                                                                                
 * *************************************************************************** */

/* adjust filename to load a different example */
val filename = "./examples/hol-tape/ntape-small.llk"  

//val filename = "./examples/hol-tape/ntape.llk"  
//val filename = "tape3old.llk"  

/* begin of proof script  */

import at.logic.gapt.language.fol.algorithms.{undoHol2Fol, replaceAbstractions, reduceHolToFol, recreateWithFactory}
import at.logic.gapt.language.hol._

import at.logic.gapt.language.fol.algorithms.undoHol2Fol

import at.logic.gapt.algorithms.hlk.HybridLatexParser
import at.logic.gapt.algorithms.rewriting.DefinitionElimination
import at.logic.gapt.proofs.lk.algorithms.{AtomicExpansion, regularize}
import at.logic.gapt.proofs.lksk.sequentToLabelledSequent
import at.logic.gapt.proofs.resolution.algorithms.RobinsonToRal


import at.logic.gapt.provers.prover9._
import at.logic.gapt.proofs.algorithms.ceres.clauseSets._
import at.logic.gapt.proofs.algorithms.ceres.projections.Projections
import at.logic.gapt.proofs.algorithms.ceres.struct.StructCreators

import at.logic.gapt.proofs.algorithms.ceres.ceres_omega
import at.logic.gapt.proofs.algorithms.herbrandExtraction.lksk.extractLKSKExpansionSequent
import at.logic.gapt.proofs.algorithms.skolemization.lksk.LKtoLKskc

 def show(s:String) = println("\n\n+++++++++ "+s+" ++++++++++\n")



 class Robinson2RalAndUndoHOL2Fol(sig_vars : Map[String, List[HOLVar]],
                                   sig_consts : Map[String, List[HOLConst]],
                                   cmap : replaceAbstractions.ConstantsMap) extends RobinsonToRal {
    val absmap = Map[String, HOLExpression]() ++ (cmap.toList.map(x => (x._2.toString, x._1)))
    val cache = Map[HOLExpression, HOLExpression]()

    override def convert_formula(e:HOLFormula) : HOLFormula = {
//      require(e.isInstanceOf[FOLFormula], "The formula "+e +" is, against our expectations, not from the fol layer." )

      BetaReduction.betaNormalize(
        recreateWithFactory( undoHol2Fol.backtranslate(e, sig_vars, sig_consts, absmap)(HOLFactory), HOLFactory).asInstanceOf[HOLFormula]  
    )
    }

    override def convert_substitution(s:HOLSubstitution) : HOLSubstitution = {
      val mapping = s.map.toList.map(x =>
        (
          BetaReduction.betaNormalize(recreateWithFactory(undoHol2Fol.backtranslate(x._1.asInstanceOf[FOLVar], sig_vars, sig_consts, absmap, None)(HOLFactory), HOLFactory).asInstanceOf[HOLExpression]).asInstanceOf[HOLVar],
          BetaReduction.betaNormalize(recreateWithFactory(undoHol2Fol.backtranslate(x._2.asInstanceOf[FOLExpression], sig_vars, sig_consts, absmap, None)(HOLFactory), HOLFactory).asInstanceOf[HOLExpression])
          )
      )

      HOLSubstitution(mapping)
    }
  }

 object Robinson2RalAndUndoHOL2Fol {
    def apply(sig_vars : Map[String, List[HOLVar]],
              sig_consts : Map[String, List[HOLConst]],
              cmap : replaceAbstractions.ConstantsMap) =
      new Robinson2RalAndUndoHOL2Fol(sig_vars, sig_consts, cmap)
  }

      show("Loading file")
      val pdb = loadLLK(filename)

      show("Eliminating definitions, expanding tautological axioms")
      val elp = AtomicExpansion(DefinitionElimination(pdb.Definitions, regularize(pdb.proof("TAPEPROOF"))))

      show("Skolemizing")
      val selp = LKtoLKskc(elp)

      show("Extracting struct")
      val struct = StructCreators.extract(selp, x => containsQuantifier(x) || freeHOVariables(x).nonEmpty)

      show("Computing projections")
      val proj = Projections(selp, x => containsQuantifier(x) || freeHOVariables(x).nonEmpty)

      show("Computing clause set")
      val cl = SimpleStandardClauseSet(struct)

      show("Exporting to prover 9")
      val (cmap, folcl_) = replaceAbstractions(cl.toList)
      val folcl = reduceHolToFol(folcl_)

      show("Refuting clause set")
      val Some(rp) = Prover9.refute(folcl)

      show("Getting formulas")
      val proofformulas = selp.nodes.flatMap(_.asInstanceOf[LKProof].root.toFSequent.formulas  ).toList.distinct

      show("Extracting signature from "+proofformulas.size+ " formulas")
      val (sigc, sigv) = undoHol2Fol.getSignature( proofformulas )

      show("Converting to Ral")

      val myconverter = Robinson2RalAndUndoHOL2Fol(sigv.map(x => (x._1, x._2.toList)), sigc.map(x => (x._1, x._2.toList)), cmap)
      val ralp = myconverter(rp)
      show("Creating acnf")
      val (acnf, endclause) = ceres_omega(proj, ralp, sequentToLabelledSequent(selp.root), struct)
      show("Compute expansion tree")
      val et = extractLKSKExpansionSequent(acnf, false)
      show(" End of script ")
