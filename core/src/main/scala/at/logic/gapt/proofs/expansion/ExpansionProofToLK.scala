package at.logic.gapt.proofs.expansion

import at.logic.gapt.proofs.lk._
import at.logic.gapt.proofs._
import at.logic.gapt.expr._
import at.logic.gapt.expr.hol.HOLPosition
import at.logic.gapt.proofs.Context.StructurallyInductiveTypes
import at.logic.gapt.proofs.sketch.UnprovableSketchInference
import at.logic.gapt.provers.escargot.Escargot

object ExpansionProofToLK extends ExpansionProofToLK( Escargot.getAtomicLKProof ) {
  def withTheory( implicit ctx: Context ) = new ExpansionProofToLK( FOTheoryMacroRule.option( _ ) )
}
object PropositionalExpansionProofToLK extends ExpansionProofToLK( _ => None )

class ExpansionProofToLK(
    theorySolver: HOLClause => Option[LKProof] ) extends SolveUtils {
  type Error = ( Seq[ETImp], ExpansionSequent )

  // TODO: type of inductions
  case class Theory( cuts: Seq[ETImp], inductions: Seq[ETImp] )

  def apply( expansionProof: ExpansionProof )( implicit ctx: Context = Context() ): UnprovableOrLKProof = {

    // TODO build induction axioms from ctx
    // constructors: basecase, c1: T -> T, c2: T*T -> T, ...
    // (X(basecase) & forall x0 X(x0) -> X(c1(x0)) & forall x0,x1 (X(x0) & X(x1)) -> X(c2(x0, x1))) -> forall x X(x)
    /*
    val indAxioms = ctx.get[StructurallyInductiveTypes].constructors.map {
      case (s, cs) => (makeInductionExplicit.inductionPrinciple(TBase(s), cs), (s, cs))
    }

    val inductions = for {
      inductionAxiomExpansion <- expansionProof.expansionSequent.antecedent
      if indAxioms.contains( inductionAxiomExpansion.shallow ) // == ETInduction.inductionAxiom
      cut <- inductionAxiomExpansion( HOLPosition( 1 ) )
      cut1 <- cut( HOLPosition( 1 ) )
      cut2 <- cut( HOLPosition( 2 ) )
    } yield (ETImp( cut1, cut2 ), indAxioms(inductionAxiomExpansion.shallow))
    println( "indAxioms: " + indAxioms )
    */
    val inductions = for {
      inductionAxiomExpansion <- expansionProof.expansionSequent.antecedent
      if ETInduction.inductionAxiom == inductionAxiomExpansion.shallow
      cut <- inductionAxiomExpansion( HOLPosition( 1 ) )
      cut1 <- cut( HOLPosition( 1 ) )
      cut2 <- cut( HOLPosition( 2 ) )
    } yield ETImp( cut1, cut2 )

    /*
    println( "inductions" )
    inductions.foreach { x => println( "induction: " + x ) }
    */

    println( "ctx: " + ctx )
    println( "ctx constructors:\n" + ctx.get[StructurallyInductiveTypes].constructors )

    solve( Theory( expansionProof.cuts, inductions ), expansionProof.nonCutPart filter { x => x.shallow != ETInduction.inductionAxiom } ).
      map( WeakeningMacroRule( _, expansionProof.nonCutPart.shallow filter { x => x != ETInduction.inductionAxiom } ) )

  }

  private def solve( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): UnprovableOrLKProof = {
    None.
      orElse( tryAxiom( theory, expSeq ) ).
      orElse( tryDef( theory, expSeq ) ).
      orElse( tryMerge( theory, expSeq ) ).
      orElse( tryWeakening( theory, expSeq ) ).
      orElse( tryNullary( theory, expSeq ) ).
      orElse( tryStrongQ( theory, expSeq ) ).
      orElse( tryWeakQ( theory, expSeq ) ).
      orElse( tryUnary( theory, expSeq ) ).
      orElse( tryCut( theory, expSeq ) ).
      orElse( tryInduction( theory, expSeq ) ).
      orElse( tryBinary( theory, expSeq ) ).
      orElse( tryTheory( theory, expSeq ) ).
      getOrElse( Left( ( theory.cuts ++ theory.inductions ) -> expSeq ) ). // TODO crashes at isSubsetOf, what about inductions here?
      map {
        ContractionMacroRule( _ ).
          ensuring { _.conclusion isSubsetOf expSeq.shallow }
      }
  }

  // TODO: remove
  private def del( expSeq: ExpansionSequent, i: SequentIndex ): ExpansionSequent = {
    //println( "before del: " + expSeq.shallow )
    //println( "deleting " + expSeq( i ).shallow )
    val ret = expSeq.delete( i )
    //println( "after del: " + ret.shallow )
    ret
  }

  // TODO: remove
  private def rtn( ret: Option[UnprovableOrLKProof], from: String ): Option[UnprovableOrLKProof] = {
    ret match {
      case Some( Right( lk ) ) => () //println( "success: " + from + "\n" + lk + "\n" )
      case _                   => ()
    }
    ret
  }

  private def tryAxiom( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val shallowSequent = expSeq.shallow
    val ret =
      if ( shallowSequent.isTaut )
        Some( Right( LogicalAxiom( shallowSequent.antecedent intersect shallowSequent.succedent head ) ) )
      else
        None

    rtn( ret, "tryAxiom" )
  }

  private def tryTheory( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = theorySolver( expSeq collect { case ETAtom( atom, _ ) => atom } ).map {
      Right( _ )
    }
    rtn( ret, "tryTheory" )
  }

  private def tryDef( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      case ( ETDefinition( sh, ch ), i ) =>
        mapIf( solve( theory, expSeq.updated( i, ch ) ), ch.shallow, i.polarity ) {
          DefinitionRule( _, ch.shallow, sh, i.polarity )
        }
    }
    rtn( ret, "tryDef" )
  }

  private def tryMerge( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      //case ( e @ ETMerge( a, b ), i: Ant ) => solve( theory, a +: b +: expSeq.delete( i ) )
      case ( e @ ETMerge( a, b ), i: Ant ) => solve( theory, a +: b +: del( expSeq, i ) )
      //case ( e @ ETMerge( a, b ), i: Suc ) => solve( theory, expSeq.delete( i ) :+ a :+ b )
      case ( e @ ETMerge( a, b ), i: Suc ) => solve( theory, del( expSeq, i ) :+ a :+ b )
    }
    rtn( ret, "tryMerge" )
  }

  private def tryNullary( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      case ( ETTop( _ ), i: Suc )    => Right( TopAxiom )
      case ( ETBottom( _ ), i: Ant ) => Right( BottomAxiom )
    }
    rtn( ret, "tryNullary" )
  }

  private def tryWeakening( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      //case ( ETWeakening( _, _ ), i ) => solve( theory, expSeq delete i )
      case ( ETWeakening( _, _ ), i ) => solve( theory, del( expSeq, i ) )
      //case ( ETTop( _ ), i: Ant )     => solve( theory, expSeq delete i )
      case ( ETTop( _ ), i: Ant )     => solve( theory, del( expSeq, i ) )
      //case ( ETBottom( _ ), i: Suc )  => solve( theory, expSeq delete i )
      case ( ETBottom( _ ), i: Suc )  => solve( theory, del( expSeq, i ) )
    }
    rtn( ret, "tryWeakening" )
  }

  private def tryUnary( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      //case ( ETNeg( f ), i: Ant ) => mapIf( solve( theory, expSeq.delete( i ) :+ f ), f.shallow, !i.polarity ) { NegLeftRule( _, f.shallow ) }
      case ( ETNeg( f ), i: Ant ) => mapIf( solve( theory, del( expSeq, i ) :+ f ), f.shallow, !i.polarity ) {
        NegLeftRule( _, f.shallow )
      }
      //case ( ETNeg( f ), i: Suc ) => mapIf( solve( theory, f +: expSeq.delete( i ) ), f.shallow, !i.polarity ) { NegRightRule( _, f.shallow ) }
      case ( ETNeg( f ), i: Suc ) => mapIf( solve( theory, f +: del( expSeq, i ) ), f.shallow, !i.polarity ) {
        NegRightRule( _, f.shallow )
      }

      case ( e @ ETAnd( f, g ), i: Ant ) =>
        //mapIf( solve( theory, f +: g +: expSeq.delete( i ) ), f.shallow, i.polarity, g.shallow, i.polarity ) {
        mapIf( solve( theory, f +: g +: del( expSeq, i ) ), f.shallow, i.polarity, g.shallow, i.polarity ) {
          AndLeftMacroRule( _, f.shallow, g.shallow )
        }
      case ( e @ ETOr( f, g ), i: Suc ) =>
        //mapIf( solve( theory, expSeq.delete( i ) :+ f :+ g ), f.shallow, i.polarity, g.shallow, i.polarity ) {
        mapIf( solve( theory, del( expSeq, i ) :+ f :+ g ), f.shallow, i.polarity, g.shallow, i.polarity ) {
          OrRightMacroRule( _, f.shallow, g.shallow )
        }
      case ( e @ ETImp( f, g ), i: Suc ) =>
        //mapIf( solve( theory, f +: expSeq.delete( i ) :+ g ), f.shallow, !i.polarity, g.shallow, i.polarity ) {
        mapIf( solve( theory, f +: del( expSeq, i ) :+ g ), f.shallow, !i.polarity, g.shallow, i.polarity ) {
          ImpRightMacroRule( _, f.shallow, g.shallow )
        }
    }
    rtn( ret, "tryUnary" )
  }

  private def tryBinary( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    def handle( i: SequentIndex, e: ExpansionTree, f: ExpansionTree, g: ExpansionTree,
                rule: ( LKProof, LKProof, Formula ) => LKProof ) =
      //solve( theory, if ( f.polarity.inSuc ) expSeq.delete( i ) :+ f else f +: expSeq.delete( i ) ) flatMap { p1 =>
      solve( theory, if ( f.polarity.inSuc ) del( expSeq, i ) :+ f else f +: del( expSeq, i ) ) flatMap { p1 =>
        if ( !p1.conclusion.contains( f.shallow, f.polarity ) ) Right( p1 )
        //else solve( theory, if ( g.polarity.inSuc ) expSeq.delete( i ) :+ g else g +: expSeq.delete( i ) ) map { p2 =>
        else solve( theory, if ( g.polarity.inSuc ) del( expSeq, i ) :+ g else g +: del( expSeq, i ) ) map { p2 =>
          if ( !p2.conclusion.contains( g.shallow, g.polarity ) ) p2
          else rule( p1, p2, e.shallow )
        }
      }

    val ret = expSeq.zipWithIndex.elements collectFirst {
      case ( e @ ETAnd( f, g ), i: Suc ) => handle( i, e, f, g, AndRightRule( _, _, _ ) )
      case ( e @ ETOr( f, g ), i: Ant )  => handle( i, e, f, g, OrLeftRule( _, _, _ ) )
      case ( e @ ETImp( f, g ), i: Ant ) => handle( i, e, f, g, ImpLeftRule( _, _, _ ) )
    }
    rtn( ret, "tryBinary" )
  }

  private def tryStrongQ( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val ret = expSeq.zipWithIndex.elements collectFirst {
      case ( ETStrongQuantifier( sh, ev, f ), i: Ant ) =>
        mapIf( solve( theory, expSeq.updated( i, f ) ), f.shallow, i.polarity ) {
          ExistsLeftRule( _, sh, ev )
        }
      case ( ETStrongQuantifier( sh, ev, f ), i: Suc ) =>
        mapIf( solve( theory, expSeq.updated( i, f ) ), f.shallow, i.polarity ) {
          ForallRightRule( _, sh, ev )
        }
      case ( ETSkolemQuantifier( sh, skT, skD, f ), i: Ant ) =>
        mapIf( solve( theory, expSeq.updated( i, f ) ), f.shallow, i.polarity ) {
          ExistsSkLeftRule( _, skT, skD )
        }
      case ( ETSkolemQuantifier( sh, skT, skD, f ), i: Suc ) =>
        mapIf( solve( theory, expSeq.updated( i, f ) ), f.shallow, i.polarity ) {
          ForallSkRightRule( _, skT, skD )
        }
    }
    rtn( ret, "tryStrongQ" )
  }

  private def tryWeakQ( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    lazy val upcomingEVs = ( for {
      et <- theory.cuts ++ theory.inductions ++ expSeq.elements
      ETStrongQuantifier( _, ev, _ ) <- et.subProofs
    } yield ev ).toSet
    def possibleInsts( insts: Map[Expr, ExpansionTree] ) =
      Map() ++ insts.filterKeys( t => freeVariables( t ) intersect upcomingEVs isEmpty )

    for ( ( ETWeakQuantifier( sh, insts ), i ) <- expSeq.zipWithIndex.elements ) {
      val insts_ = possibleInsts( insts )

      if ( insts_.nonEmpty ) {
        var newExpSeq =
          //if ( insts_ == insts ) expSeq.delete( i )
          if ( insts_ == insts ) del( expSeq, i )
          else expSeq.updated( i, ETWeakQuantifier( sh, insts -- insts_.keys ) )

        if ( i isSuc ) newExpSeq :++= insts_.values
        else newExpSeq ++:= insts_.values

        rtn( return Some( solve( theory, newExpSeq ) map { p0 =>
          insts_.foldLeft( p0 ) {
            case ( p, ( _, child ) ) if !p.conclusion.contains( child.shallow, i.polarity ) => p
            case ( p, ( t, _ ) ) if i isAnt => ForallLeftRule( p, sh, t )
            case ( p, ( t, _ ) ) if i isSuc => ExistsRightRule( p, sh, t )
          }
        } ), "tryWeakQ" )
      }
    }

    None
  }

  private def tryCut( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    lazy val upcomingEVs = ( for {
      et <- theory.cuts ++ theory.inductions ++ expSeq.elements
      ETStrongQuantifier( _, ev, _ ) <- et.subProofs
    } yield ev ).toSet

    val ret = theory.cuts.zipWithIndex collectFirst {
      case ( ETImp( cut1, cut2 ), i ) if freeVariables( cut1.shallow ) intersect upcomingEVs isEmpty =>
        val newCuts = theory.cuts.zipWithIndex.filter { _._2 != i }.map { _._1 }
        solve( Theory( newCuts, theory.inductions ), expSeq :+ cut1 ) flatMap { p1 =>
          if ( !p1.conclusion.contains( cut1.shallow, Polarity.InSuccedent ) ) Right( p1 )
          else solve( Theory( newCuts, theory.inductions ), cut2 +: expSeq ) map { p2 =>
            if ( !p2.conclusion.contains( cut2.shallow, Polarity.InAntecedent ) ) p2
            else CutRule( p1, p2, cut1.shallow )
          }
        }
    }
    rtn( ret, "tryCut" )
  }

  private def tryInduction( theory: Theory, expSeq: ExpansionSequent )( implicit ctx: Context ): Option[UnprovableOrLKProof] = {
    val upcomingEVs = ( for {
      et <- theory.cuts ++ theory.inductions ++ expSeq.elements
      ETStrongQuantifier( _, ev, _ ) <- et.subProofs
    } yield ev ).toSet

    val ret = theory.inductions.zipWithIndex.collectFirst {
      // TODO handle more than 2 conjuncts with more constructors
      case ( ETImp( ant @ ETAnd( ant1, ETStrongQuantifier( _, ev, ETImp( ch1, ch2 ) ) ), suc: ETWeakQuantifier ), i ) if freeVariables( ant.shallow ) intersect upcomingEVs isEmpty =>
        val newInductions = theory.inductions.zipWithIndex.filter { _._2 != i }.map { _._1 }

        // TODO: recurse over ant
        val ret = solve( Theory( theory.cuts, newInductions ), expSeq :+ ant1 ) flatMap { p1 =>
          if ( !p1.conclusion.contains( ant1.shallow, Polarity.InSuccedent ) ) Right( p1 )
          else solve( Theory( theory.cuts, newInductions ), ch1 +: expSeq :+ ch2 ) flatMap { p2 =>
            if ( !p2.conclusion.contains( ch1.shallow, Polarity.InAntecedent )
              || !p2.conclusion.contains( ch2.shallow, Polarity.InSuccedent ) ) Right( p2 )
            else {
              val index1 = p1.conclusion.indexOf( ant1.shallow, Polarity.InSuccedent )
              val index2 = p2.conclusion.indexOf( ch1.shallow, Polarity.InAntecedent )
              val index3 = p2.conclusion.indexOf( ch2.shallow, Polarity.InSuccedent )
              val cases = Seq(
                InductionCase( p1, hoc"0:nat", Seq.empty, Seq.empty, index1 ),
                InductionCase( p2, hoc"s:nat>nat", Seq( index2 ), Seq( ev ), index3 ) )

              val App( _, qfFormula: Abs ) = suc.shallow
              val ( v: Expr, _ ) = suc.instances.head

              val ir = InductionRule( cases, qfFormula, v )
              val phit = ETAtom( Atom( ir.conclusion.succedent.head ), Polarity.InAntecedent )
              val r = solve( Theory( theory.cuts, newInductions ), phit +: expSeq ) map { p3 =>
                if ( !p3.conclusion.contains( phit.shallow, Polarity.InAntecedent ) )
                  p3
                else
                  // TODO simplify to ir if p3 is just an axiom?
                  CutRule( ir, p3, phit.shallow )
              }
              r
            }
          }
        }
        ret
    }
    rtn( ret, "tryInduction" )

  }

}
