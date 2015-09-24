
package at.logic.gapt.formats.ivy

import at.logic.gapt.formats.lisp._
import at.logic.gapt.expr._
import at.logic.gapt.expr.fol.FOLSubstitution
import at.logic.gapt.proofs.resolutionOld._
import at.logic.gapt.proofs.lk.base._
import at.logic.gapt.proofs.occurrences.FormulaOccurrence
import at.logic.gapt.proofs.{ HOLSequent, occurrences }
import at.logic.gapt.utils.logging.Logger

/**
 * Implements parsing of ivy format: https://www.cs.unm.edu/~mccune/papers/ivy/ into Ivy's Resolution calculus.
 */

/* Constructor object, takes a filename and tries to parse as a lisp_file  */
object IvyParser extends Logger {
  override def loggerName = "IvyParserLogger"

  //calls the sexpression parser on the given file and parses it, needs a naming convention
  def apply( fn: String ): IvyResolutionProof =
    parse( SExpressionParser.parseFile( fn ) )

  def parseString( sexpr: String ): IvyResolutionProof =
    parse( SExpressionParser.parseString( sexpr ) )

  def parse( exp: Seq[SExpression] ): IvyResolutionProof = {
    require( exp.length >= 1, "An ivy proof must contain at least one proof object, not " + exp.length + "! " )
    if ( exp.length > 1 ) warn( "WARNING: Ivy proof contains more than one proof, taking the first one." )
    parse( exp( 0 ) )
  }

  // the type synoyms should make the parsing functions more readable
  type ProofId = String
  type ProofMap = Map[ProofId, IvyResolutionProof]
  type Position = List[Int]

  //decompose the proof object to a list and hand it to parse(exp: List[SExpression], found_steps : ProofMap )
  def parse( exp: SExpression ): IvyResolutionProof = exp match {
    case LList()         => throw new Exception( "Trying to parse an empty proof!" )
    case LList( l @ _* ) => parse_steps( l ) // extract the list of inferences from exp
    case _               => throw new Exception( "Parsing error: The proof object is not a list!" )
  }

  /* traverses the list of inference sexpressions and returns an IvyResolution proof - this can then be translated to
   * our resolution calculus (i.e. where instantiation is contained in the substitution)
   * note: requires that an if inference a references inference b, then a occurs before b in the list */
  def parse_steps( exp: Seq[SExpression] ): IvyResolutionProof = {
    var lastid: ProofId = null
    var found_steps: ProofMap = Map[String, IvyResolutionProof]()

    exp foreach { step =>
      val ( new_lastid, new_found_steps ) = parse_step( step, found_steps )
      lastid = new_lastid
      found_steps = new_found_steps
    }

    found_steps( lastid )
  }

  /* parses an inference step and updates the proof map  */
  def parse_step( exp: SExpression, found_steps: ProofMap ): ( ProofId, ProofMap ) = {
    exp match {
      /* ================== Atom ========================== */
      case LFun( id, LFun( "input" ), clause, _* ) => {
        val fclause = parse_clause( clause )

        val inference = InitialClause( id, clause,
          OccClause(
            fclause.antecedent map ( occurrences.factory.createFormulaOccurrence( _, Nil ) ),
            fclause.succedent map ( occurrences.factory.createFormulaOccurrence( _, Nil ) )
          ) )

        require( inference.root.toHOLSequent setEquals fclause, "Error in Atom parsing: required result=" + fclause + " but got: " + inference.root )
        ( id, found_steps + ( ( id, inference ) ) )
      }

      /* ================== Instance ========================== */
      case LFun( id, LFun( "instantiate", LAtom( parent_id ), subst_exp ), clause, _* ) => {
        val parent_proof = found_steps( parent_id )
        val sub: FOLSubstitution = parse_substitution( subst_exp )
        val fclause: HOLSequent = parse_clause( clause )

        def connect( ancestors: Seq[FormulaOccurrence], formulas: Seq[HOLFormula] ): Seq[FormulaOccurrence] =
          ( ancestors zip formulas ) map ( ( v: ( FormulaOccurrence, HOLFormula ) ) =>
            occurrences.factory.createFormulaOccurrence( v._2, List( v._1 ) ) )

        val inference = Instantiate( id, clause, sub,
          OccClause(
            connect( parent_proof.vertex.antecedent, fclause.antecedent ),
            connect( parent_proof.vertex.succedent, fclause.succedent )
          ), parent_proof )

        require( inference.root.toHOLSequent setEquals fclause, "Error in Instance parsing: required result=" + fclause + " but got: " + inference.root )
        ( id, found_steps + ( ( id, inference ) ) )

      }

      /* ================== Resolution ========================== */
      case LFun( id, LFun( "resolve",
        LAtom( parent_id1 ), LList( position1 @ _* ),
        LAtom( parent_id2 ), LList( position2 @ _* ) ), clause, _* ) => {
        val parent_proof1 = found_steps( parent_id1 )
        val parent_proof2 = found_steps( parent_id2 )
        val fclause: HOLSequent = parse_clause( clause )

        val ( occ1, polarity1, _ ) = get_literal_by_position( parent_proof1.vertex, position1, parent_proof1.clause_exp )
        val ( occ2, polarity2, _ ) = get_literal_by_position( parent_proof2.vertex, position2, parent_proof2.clause_exp )

        require( occ1.formula == occ2.formula, "Resolved formula " + occ1.formula + " must be equal to " + occ2.formula + " !" )

        def connect( c1: OccClause, c2: OccClause, conclusion: HOLSequent ): OccClause = {
          conclusion match {
            //process antecedent
            case HOLSequent( x :: xs, ys ) =>
              val pos1 = c1.antecedent indexWhere ( _.formula == x )
              if ( pos1 >= 0 ) {
                val focc = c1.antecedent( pos1 ).factory.createFormulaOccurrence( x, c1.antecedent( pos1 ).parents )
                val rec = connect( OccClause( c1.antecedent.filterNot( _ == c1.antecedent( pos1 ) ), c1.succedent ), c2, HOLSequent( xs, ys ) )
                OccClause( focc :: rec.antecedent.toList, rec.succedent )
              } else {
                val pos2 = c2.antecedent indexWhere ( _.formula == x )
                if ( pos2 >= 0 ) {
                  val focc = c2.antecedent( pos2 ).factory.createFormulaOccurrence( x, c2.antecedent( pos2 ).parents )
                  val rec = connect( c1, OccClause( c2.antecedent.filterNot( _ == c2.antecedent( pos2 ) ), c2.succedent ), HOLSequent( xs, ys ) )
                  OccClause( focc :: rec.antecedent.toList, rec.succedent )
                } else throw new Exception( "Error in parsing resolution inference: resolved literal " + x + " not found!" )
              }
            //then succedent
            case HOLSequent( Nil, y :: ys ) =>
              val pos1 = c1.succedent indexWhere ( _.formula == y )
              if ( pos1 >= 0 ) {
                val focc = c1.succedent( pos1 ).factory.createFormulaOccurrence( y, c1.succedent( pos1 ).parents )
                val rec = connect( OccClause( c1.antecedent, c1.succedent.filterNot( _ == c1.succedent( pos1 ) ) ), c2, HOLSequent( Nil, ys ) )
                OccClause( rec.antecedent, focc :: rec.succedent.toList )
              } else {
                val pos2 = c2.succedent indexWhere ( _.formula == y )
                if ( pos2 >= 0 ) {
                  val focc = c2.succedent( pos2 ).factory.createFormulaOccurrence( y, c2.succedent( pos2 ).parents )
                  val rec = connect( c1, OccClause( c2.antecedent, c2.succedent.filterNot( _ == c2.succedent( pos2 ) ) ), HOLSequent( Nil, ys ) )
                  OccClause( rec.antecedent, focc :: rec.succedent.toList )
                } else throw new Exception( "Error in parsing resolution inference: resolved literal " + y + " not found!" )
              }
            //base case
            case HOLSequent( Nil, Nil ) => OccClause( Nil, Nil )
            case _                      => throw new Exception( "Unhandled case in calculation of ancestor relationship during creation of a resolution iference!" )
          }
        }

        ( polarity1, polarity2 ) match {
          case ( true, false ) =>
            val clause1 = OccClause( parent_proof1.vertex.antecedent, parent_proof1.vertex.succedent filterNot ( _ == occ1 ) )
            val clause2 = OccClause( parent_proof2.vertex.antecedent filterNot ( _ == occ2 ), parent_proof2.vertex.succedent )
            val inference = Resolution( id, clause, occ1, occ2, connect( clause1, clause2, fclause ), parent_proof1, parent_proof2 )

            require( inference.root.toHOLSequent setEquals fclause, "Error in Resolution parsing: required result=" + fclause + " but got: " + inference.root )
            ( id, found_steps + ( ( id, inference ) ) )

          case ( false, true ) =>
            val clause1 = OccClause( parent_proof1.vertex.antecedent filterNot ( _ == occ1 ), parent_proof1.vertex.succedent )
            val clause2 = OccClause( parent_proof2.vertex.antecedent, parent_proof2.vertex.succedent filterNot ( _ == occ2 ) )
            val inference = Resolution( id, clause, occ1, occ2, connect( clause1, clause2, fclause ), parent_proof1, parent_proof2 )

            require( inference.root.toHOLSequent setEquals fclause, "Error in Resolution parsing: required result=" + fclause + " but got: " + inference.root )
            ( id, found_steps + ( ( id, inference ) ) )

          case _ =>
            throw new Exception( "Error parsing resolution inference: must resolve over a positive and a negative literal!" )
        }
      }

      /* ================== Flip ========================== */
      case LFun( id, LFun( "flip", LAtom( parent_id ), LList( position @ _* ) ), clause, _* ) =>
        val parent_proof = found_steps( parent_id )
        val fclause = parse_clause( clause )
        val ( occ, polarity, _ ) = get_literal_by_position( parent_proof.root, position, parent_proof.clause_exp )

        occ.formula match {
          case Eq( left, right ) =>
            //the negative literals are the same
            def connect_directly( x: FormulaOccurrence ) = x.factory.createFormulaOccurrence( x.formula, x :: Nil )

            polarity match {
              case true =>
                val neglits = parent_proof.root.negative map connect_directly
                val ( pos1, pos2 ) = parent_proof.root.positive.splitAt( parent_proof.root.positive.indexOf( occ ) )
                val ( pos1_, pos2_ ) = ( pos1 map connect_directly, pos2 map connect_directly )
                val flipped = occ.factory.createFormulaOccurrence( Eq( right, left ), occ :: Nil )
                val inference = Flip( id, clause, flipped, OccClause( neglits, pos1_ ++ List( flipped ) ++ pos2_.tail ), parent_proof )
                require(
                  fclause setEquals inference.root.toHOLSequent,
                  "Error parsing flip rule: inferred clause " + inference.root.toHOLSequent +
                    " is not the same as given clause " + fclause
                )
                ( id, found_steps + ( ( id, inference ) ) )

              case false =>
                val poslits = parent_proof.root.positive map connect_directly
                val ( neg1, neg2 ) = parent_proof.root.negative.splitAt( parent_proof.root.negative.indexOf( occ ) )
                val ( neg1_, neg2_ ) = ( neg1 map connect_directly, neg2 map connect_directly )
                val flipped = occ.factory.createFormulaOccurrence( Eq( right, left ), occ :: Nil )
                val inference = Flip( id, clause, flipped, OccClause( neg1_ ++ List( flipped ) ++ neg2_.tail, poslits ), parent_proof )
                require(
                  fclause setEquals inference.root.toHOLSequent,
                  "Error parsing flip rule: inferred clause " + inference.root.toHOLSequent +
                    " is not the same as given clause " + fclause
                )
                ( id, found_steps + ( ( id, inference ) ) )
            }

          case _ =>
            throw new Exception( "Error parsing position in flip rule: literal " + occ.formula + " is not the equality predicate." )
        }

      /* ================== Paramodulation ========================== */
      case LFun( id, LFun( "paramod",
        LAtom( modulant_id ), LList( mposition @ _* ),
        LAtom( parent_id ), LList( pposition @ _* )
        ), clause, _* ) =>
        val modulant_proof = found_steps( modulant_id )
        val parent_proof = found_steps( parent_id )
        val fclause = parse_clause( clause )
        val ( mocc, mpolarity, direction ) = get_literal_by_position( modulant_proof.root, mposition, modulant_proof.clause_exp )
        require( direction == List( 1 ) || direction == List( 2 ), "Must indicate if paramod or demod!" )
        val orientation = if ( direction.head == 1 ) true else false //true = paramod (left to right), false = demod (right to left)

        require( mpolarity == true, "Paramodulated literal must be positive!" )
        val ( pocc, polarity, int_position ) = get_literal_by_position( parent_proof.root, pposition, parent_proof.clause_exp )

        mocc.formula match {
          case Eq( left: FOLTerm, right: FOLTerm ) =>
            def connect_directly( x: FormulaOccurrence ) = x.factory.createFormulaOccurrence( x.formula, x :: Nil )
            polarity match {
              case true =>
                val neglits = parent_proof.root.negative map connect_directly
                val ( pneg, ppos ) = ( modulant_proof.root.negative map connect_directly, modulant_proof.root.positive.filterNot( _ == mocc ) map connect_directly )
                val ( pos1, pos2 ) = parent_proof.root.positive.splitAt( parent_proof.root.positive.indexOf( pocc ) )
                val ( pos1_, pos2_ ) = ( pos1 map connect_directly, pos2 map connect_directly )

                val paraformula = if ( orientation )
                  replaceTerm_by_in_at( left, right, pocc.formula.asInstanceOf[FOLFormula], int_position ).asInstanceOf[FOLFormula]
                else
                  replaceTerm_by_in_at( right, left, pocc.formula.asInstanceOf[FOLFormula], int_position ).asInstanceOf[FOLFormula]
                val para = pocc.factory.createFormulaOccurrence( paraformula, mocc :: pocc :: Nil )

                val inferred_clause = OccClause( pneg ++ neglits, ppos ++ pos1_ ++ List( para ) ++ pos2_.tail )

                val inference = Paramodulation( id, clause, int_position, para, orientation, inferred_clause, modulant_proof, parent_proof )
                require( inference.root.toHOLSequent setEquals fclause, "Error in Paramodulation parsing: required result=" + fclause + " but got: " + inference.root )

                ( id, found_steps + ( ( id, inference ) ) )

              case false =>
                val poslits = parent_proof.root.positive map connect_directly
                val ( pneg, ppos ) = ( modulant_proof.root.negative map connect_directly, modulant_proof.root.positive.filterNot( _ == mocc ) map connect_directly )
                val ( neg1, neg2 ) = parent_proof.root.negative.splitAt( parent_proof.root.negative.indexOf( pocc ) )
                val ( neg1_, neg2_ ) = ( neg1 map connect_directly, neg2 map connect_directly )

                val paraformula = if ( orientation )
                  replaceTerm_by_in_at( left, right, pocc.formula.asInstanceOf[FOLFormula], int_position ).asInstanceOf[FOLFormula]
                else
                  replaceTerm_by_in_at( right, left, pocc.formula.asInstanceOf[FOLFormula], int_position ).asInstanceOf[FOLFormula]

                val para = pocc.factory.createFormulaOccurrence( paraformula, mocc :: pocc :: Nil )
                val inferred_clause = OccClause( pneg ++ neg1_ ++ List( para ) ++ neg2_.tail, ppos ++ poslits )

                val inference = Paramodulation( id, clause, int_position, para, orientation, inferred_clause, modulant_proof, parent_proof )

                require( inference.root.toHOLSequent setEquals fclause, "Error in Paramodulation parsing: required result=" + fclause + " but got: " + inference.root )
                ( id, found_steps + ( ( id, inference ) ) )
            }

          case _ =>
            throw new Exception( "Error parsing position in paramod rule: literal " + mocc.formula + " is not the equality predicate." )

        }

      /* ================== Propositional ========================== */
      case LFun( id, LFun( "propositional", LAtom( parent_id ) ), clause, _* ) => {
        val parent_proof = found_steps( parent_id )
        val fclause: HOLSequent = parse_clause( clause )

        def list_withoutn[A]( l: List[A], n: Int ): List[A] = l match {
          case x :: xs =>
            if ( n == 0 ) xs else x :: list_withoutn( xs, n - 1 )
          case Nil => Nil
        }

        //connects ancestors to formulas
        def connect( ancestors: List[FormulaOccurrence], formulas: List[HOLFormula] ): List[FormulaOccurrence] = {
          //find ancestor for every formula in conclusion clause
          val ( occs, rem ) = connect_( ancestors, formulas )
          //now connect the contracted formulas
          val connected: List[FormulaOccurrence] = connect_missing( occs, rem )
          connected
        }

        //connects each formula to an ancestor, returns a pair of connected formulas and unconnected ancestors
        def connect_( ancestors: List[FormulaOccurrence], formulas: List[HOLFormula] ): ( List[FormulaOccurrence], List[FormulaOccurrence] ) = {
          formulas match {
            case x :: xs =>
              val index = ancestors.indexWhere( _.formula == x )
              require( index >= 0, "Error connecting ancestors in propositional ivy inference: formula " + x + " does not occur in ancestors " + ancestors )
              val anc = ancestors( index )
              val occ = anc.factory.createFormulaOccurrence( x, anc :: Nil )
              val ( occs, rem ) = connect_( list_withoutn( ancestors, index ), xs )

              ( occ :: occs, rem )

            case Nil => ( Nil, ancestors )
          }
        }

        //connects unconnected (missing) ancestors to list of potential targets, returns list of updated targets
        def connect_missing( targets: List[FormulaOccurrence], missing: List[FormulaOccurrence] ): List[FormulaOccurrence] = missing match {
          case x :: xs =>
            val targets_ = connect_missing_( targets, x )
            connect_missing( targets_, xs )
          case Nil =>
            targets
        }

        //connects one missing occurence to possible tagets, returns list of updated targets
        def connect_missing_( targets: List[FormulaOccurrence], missing: FormulaOccurrence ): List[FormulaOccurrence] = targets match {
          case x :: xs =>
            if ( missing.formula == x.formula )
              List( x.factory.createFormulaOccurrence( x.formula, List( missing ) ++ x.parents ) ) ++ xs
            else
              List( x ) ++ connect_missing_( xs, missing )
          case Nil =>
            throw new Exception( "Error connecting factorized literal, no suitable successor found!" )
        }

        val inference = Propositional( id, clause,
          OccClause(
            connect( parent_proof.vertex.antecedent.toList, fclause.antecedent.toList ),
            connect( parent_proof.vertex.succedent.toList, fclause.succedent.toList )
          ), parent_proof )

        require( inference.root.toHOLSequent setEquals fclause, "Error in Propositional parsing: required result=" + fclause + " but got: " + inference.root )
        ( id, found_steps + ( ( id, inference ) ) )

      }

      // new symbol
      case LFun( id, LFun( "new_symbol", LAtom( parent_id ) ), clause, _* ) =>

        val parent_proof = found_steps( parent_id )
        val fclause: HOLSequent = parse_clause( clause )
        require( fclause.antecedent.isEmpty, "Expecting only positive equations in parsing of new_symbol rule " + id )
        require( fclause.succedent.size == 1, "Expecting exactly one positive equation in parsing of new_symbol rule " + id )

        val Eq( l: FOLTerm, r ) = fclause.succedent( 0 )

        val nclause = OccClause( Nil, List( parent_proof.root.occurrences( 0 ).factory.createFormulaOccurrence( fclause.succedent( 0 ), Nil ) ) )
        val const: FOLConst = r match {
          case f @ FOLConst( _ ) => f.asInstanceOf[FOLConst]
          case _                 => throw new Exception( "Expecting right hand side of new_symbol equation to be the introduced symbol!" )
        }

        val inference = NewSymbol( id, clause, nclause.succedent( 0 ), const, l, nclause, parent_proof )

        ( id, found_steps + ( ( id, inference ) ) )

      case _ => throw new Exception( "Error parsing inference rule in expression " + exp )
    }
  }

  //extracts a literal from a clause - since the clause seperates positive and negative clauses,
  // we also need the original SEXpression to make sense of the position.
  // paramodulation continues inside the term, so we return the remaining position together with the occurrence
  // the boolean indicates a positive or negative formula

  def get_literal_by_position( c: OccClause, pos: Seq[SExpression],
                               clauseexp: SExpression ): ( FormulaOccurrence, Boolean, List[Int] ) = {
    val ipos = parse_position( pos )
    val ( iformula, termpos ) = parse_clause_frompos( clauseexp, ipos )
    //Remark: we actually return the first occurrence of the formula, not the one at the position indicated as
    //        it should not make a difference. (if f occurs twice in the clause, it might be derived differently
    //        but we usually don't care for that)
    iformula match {
      case a @ FOLAtom( sym, args ) =>
        c.positive.find( _.formula == a ) match {
          case Some( occ ) =>
            ( occ, true, termpos )
          case None =>
            throw new Exception( "Error in getting literal by position! Could not find " + iformula + " in " + c )
        }

      case Neg( a @ FOLAtom( sym, args ) ) =>
        c.negative.find( _.formula == a ) match {
          case Some( occ ) =>
            ( occ, false, termpos )
          case None =>
            throw new Exception( "Error in getting literal by position! Could not find " + iformula + " in " + c )
        }
    }
  }

  //term replacement
  //TODO: refactor replacement for lambda expressions
  def replaceTerm_by_in_at( what: FOLTerm, by: FOLTerm, exp: FOLExpression, pos: List[Int] ): FOLExpression = pos match {
    case p :: ps =>
      exp match {
        case FOLAtom( sym, args ) =>
          require( 1 <= p && p <= args.length, "Error in parsing replacement: invalid argument position in atom!" )
          val ( args1, rterm :: args2 ) = args.splitAt( p - 1 )
          FOLAtom( sym, ( args1 ++ List( replaceTerm_by_in_at( what, by, rterm, ps ).asInstanceOf[FOLTerm] ) ++ args2 ) )
        case FOLFunction( sym, args ) =>
          require( 1 <= p && p <= args.length, "Error in parsing replacement: invalid argument position in function!" )
          val ( args1, rterm :: args2 ) = args.splitAt( p - 1 )
          FOLFunction( sym, ( args1 ++ List( replaceTerm_by_in_at( what, by, rterm, ps ).asInstanceOf[FOLTerm] ) ++ args2 ) )
        case _ => throw new Exception( "Error in parsing replacement: unexpected (sub)term " + exp + " )" )
      }

    case Nil =>
      if ( exp == what ) by else throw new Exception( "Error in parsing replacement: (sub)term " + exp + " is not the expected term " + what )
  }

  def parse_position( l: Seq[SExpression] ): List[Int] = l.toList map { case LAtom( s ) => s.toInt }

  def parse_substitution( exp: SExpression ): FOLSubstitution = exp match {
    case LList( list @ _* ) =>
      FOLSubstitution( parse_substitution_( list ) )
    case _ => throw new Exception( "Error parsing substitution expression " + exp + " (not a list)" )
  }

  //Note:substitution are sometimes given as lists of cons and sometimes as two-element list...
  def parse_substitution_( exp: Seq[SExpression] ): List[( FOLVar, FOLTerm )] = exp.toList map {
    case LList( vexp, texp @ _* ) =>
      val v = parse_term( vexp )
      val t = parse_term( LList( texp: _* ) )

      v.asInstanceOf[FOLVar] -> t
    case LCons( vexp, texp ) =>
      val v = parse_term( vexp )
      val t = parse_term( texp )

      v.asInstanceOf[FOLVar] -> t
  }

  /* parses a clause sexpression to a fclause -- the structure is (or lit1 (or lit2 .... (or litn-1 litn)...)) */
  def parse_clause( exp: SExpression ): HOLSequent = {
    val clauses = parse_clause_( exp )
    var pos: List[HOLFormula] = Nil
    var neg: List[HOLFormula] = Nil

    for ( c <- clauses ) {
      c match {
        case Neg( formula ) =>
          formula match {
            case FOLAtom( _, _ ) => neg = formula :: neg
            case _               => throw new Exception( "Error parsing clause: negative Literal " + formula + " is not an atom!" )
          }
        case FOLAtom( _, _ ) =>
          pos = c :: pos
        case _ =>
          throw new Exception( "Error parsing clause: formula " + c + " is not a literal!" )
      }
    }

    //the literals were prepended to the list, so we have to reverse them to get the original order
    HOLSequent( neg.reverse, pos.reverse )
  }

  //TODO: merge code with parse_clause_
  def parse_clause_frompos( exp: SExpression, pos: List[Int] ): ( HOLFormula, List[Int] ) = exp match {
    case LFun( "or", left, right ) =>
      pos match {
        case 1 :: rest =>
          left match {
            case LFun( "not", LFun( name, args @ _* ) ) =>
              val npos = if ( rest.isEmpty ) rest else rest.tail //if we point to a term we have to strip the indicator for neg
              ( Neg( parse_atom( name, args ) ), npos )
            case LFun( name, args @ _* ) =>
              ( parse_atom( name, args ), rest )
            case _ => throw new Exception( "Parsing Error: unexpected element " + exp + " in parsing of Ivy proof object." )
          }
        case 2 :: rest =>
          parse_clause_frompos( right, rest )
        case _ => throw new Exception( "pos " + pos + " did not point to a literal!" )
      }

    case LFun( "not", LFun( name, args @ _* ) ) =>
      val npos = if ( pos.isEmpty ) pos else pos.tail //if we point to a term we have to strip the indicator for neg
      ( Neg( parse_atom( name, args ) ), npos )

    case LFun( name, args @ _* ) =>
      ( parse_atom( name, args ), pos )

    //the empty clause is denoted by false
    case LAtom( "false" ) =>
      throw new Exception( "Parsing Error: want to extract literal from empty clause!" )

    case _ => throw new Exception( "Parsing Error: unexpected element " + exp + " in parsing of Ivy proof object." )
  }

  //directly converts a clause as nested or expression into a list with the literals in the same order
  def parse_clause_( exp: SExpression ): List[HOLFormula] = exp match {
    case LFun( "or", left, right ) =>
      val rightclause = parse_clause_( right )

      left match {
        case LFun( "not", LFun( name, args @ _* ) ) =>
          Neg( parse_atom( name, args ) ) :: rightclause
        case LFun( name, args @ _* ) =>
          parse_atom( name, args ) :: rightclause
        case _ => throw new Exception( "Parsing Error: unexpected element " + exp + " in parsing of Ivy proof object." )
      }

    case LFun( "not", LFun( name, args @ _* ) ) =>
      Neg( parse_atom( name, args ) ) :: Nil

    case LFun( name, args @ _* ) =>
      parse_atom( name, args ) :: Nil

    //the empty clause is denoted by false
    case LAtom( "false" ) =>
      List()

    case _ => throw new Exception( "Parsing Error: unexpected element " + exp + " in parsing of Ivy proof object." )
  }

  def parse_atom( name: String, args: Seq[SExpression] ) = {
    val argterms = args map parse_term
    if ( name == "=" ) {
      require( args.length == 2, "Error parsing equality: = must be a binary predicate!" )
      Eq( argterms( 0 ), argterms( 1 ) )
    } else {
      FOLAtom( name, argterms )
    }

  }

  //some names are escaped for ivy, see also  LADR-2009-11A/ladr/ivy.c in the Prover9 source
  val ivy_escape_table = Map[String, String](
    ( "zero_for_ivy", "0" ),
    ( "one_for_ivy", "1" ),
    ( "quote_for_ivy", "'" ),
    ( "backslash_for_ivy", "\\\\" ),
    ( "at_for_ivy", "@" ),
    ( "meet_for_ivy", "^" )
  )
  def rewrite_name( s: String ): String = if ( ivy_escape_table contains s ) ivy_escape_table( s ) else s

  def parse_term( ts: SExpression ): FOLTerm = ts match {
    case LAtom( name ) =>
      val rname = rewrite_name( name )
      FOLVar( rname )
    //the proof might contain the constant nil which is parsed to an empty LList. in this case the empty list
    //corresponds to a constant
    case LList( LList() ) =>
      FOLConst( "nil" )
    case LFun( name, args @ _* ) =>
      val rname = rewrite_name( name )
      FOLFunction( rname, args.map( parse_term( _ ) ) )
    case _ =>
      throw new Exception( "Parsing Error: Unexpected expression " + ts + " in parsing of a term." )
  }

}
