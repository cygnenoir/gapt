/*
 * Simple functions that operate on HOLExpressions, HOLFormulas and sequents.
 */

package at.logic.gapt.language.hol

import at.logic.gapt.language.hol.logicSymbols._
import at.logic.gapt.proofs.lk.base.FSequent
import at.logic.gapt.language.lambda.symbols.StringSymbol
import at.logic.gapt.language.lambda.types.{ TA, Ti }
import at.logic.gapt.language.lambda.{ freeVariables => freeVariablesLambda, rename => renameLambda }

object freeVariables {
  /**
   * Computes a list of all HOL Variables of an expression, including repetitions.
   * @param e the expressions to extract from
   * @return the list of free variables in e
   */
  def apply( e: HOLExpression ): List[HOLVar] = freeVariablesLambda( e ).asInstanceOf[List[HOLVar]]
}

object freeHOVariables {
  /**
   * Computes a list of all HOL Variables which are not of type i in a formula, including repetitions. Together with
   * checks on quantifiers, this can be used to decide if a formula has "true" higher-order content.
   * @param f the expressions to extract from
   * @return the list of free variables with type != Ti in e
   */
  def apply( f: HOLFormula ) = freeVariables( f ).filter( _ match { case HOLVar( _, Ti ) => false; case _ => true } )
}

// matches for consts and vars, but nothing else
object VarOrConst {
  def unapply( e: HOLExpression ): Option[( String, TA )] = e match {
    case HOLVar( name, et )   => Some( ( name, et ) )
    case HOLConst( name, et ) => Some( ( name, et ) )
    case _                    => None
  }
}

// get a new variable/constant (similar to the current and) different from all 
// variables/constants in the blackList, returns this variable if this variable 
// is not in the blackList
object rename {
  def apply( v: HOLVar, blacklist: List[HOLVar] ): HOLVar = renameLambda( v, blacklist ).asInstanceOf[HOLVar]
}

// Instantiates a term in a quantified formula (using the first quantifier).
object instantiate {
  def apply( f: HOLFormula, t: HOLExpression ): HOLFormula = f match {
    case HOLAllVar( v, form ) =>
      val sub = HOLSubstitution( v, t )
      sub( form )
    case HOLExVar( v, form ) =>
      val sub = HOLSubstitution( v, t )
      sub( form )
    case _ => throw new Exception( "ERROR: trying to replace variables in a formula without quantifier." )
  }
}

object containsQuantifier {
  def apply( e: HOLExpression ): Boolean = e match {
    case HOLVar( x, tpe )   => false
    case HOLConst( x, tpe ) => false
    case HOLAtom( x, args ) => false
    case HOLAnd( x, y )     => containsQuantifier( x ) || containsQuantifier( y )
    case HOLOr( x, y )      => containsQuantifier( x ) || containsQuantifier( y )
    case HOLImp( x, y )     => containsQuantifier( x ) || containsQuantifier( y )
    case HOLNeg( x )        => containsQuantifier( x )
    case HOLExVar( x, f )   => true
    case HOLAllVar( x, f )  => true
    // Is this really necessary?
    case HOLAbs( v, exp )   => containsQuantifier( exp )
    case HOLApp( l, r )     => containsQuantifier( l ) || containsQuantifier( r )
    case _                  => throw new Exception( "Unrecognized symbol." )
  }
}

object containsStrongQuantifier {
  def apply( f: HOLFormula, pol: Boolean ): Boolean = f match {
    case HOLAtom( _, _ )   => false
    case HOLAnd( s, t )    => containsStrongQuantifier( s, pol ) || containsStrongQuantifier( t, pol )
    case HOLOr( s, t )     => containsStrongQuantifier( s, pol ) || containsStrongQuantifier( t, pol )
    case HOLImp( s, t )    => containsStrongQuantifier( s, !pol ) || containsStrongQuantifier( t, pol )
    case HOLNeg( s )       => containsStrongQuantifier( s, !pol )
    case HOLAllVar( x, s ) => if ( pol == true ) true else containsStrongQuantifier( s, pol )
    case HOLExVar( x, s )  => if ( pol == false ) true else containsStrongQuantifier( s, pol )
    case _                 => throw new Exception( "Unhandled case!" )
  }

  def apply( s: FSequent ): Boolean =
    s.antecedent.exists( x => containsStrongQuantifier( x, false ) ) ||
      s.succedent.exists( x => containsStrongQuantifier( x, true ) )
}

object isPrenex {
  def apply( e: HOLExpression ): Boolean = e match {
    case HOLVar( _, _ )    => true
    case HOLConst( _, _ )  => true
    case HOLAtom( _, _ )   => true
    case HOLNeg( f )       => !containsQuantifier( f )
    case HOLAnd( f1, f2 )  => !containsQuantifier( f1 ) && !containsQuantifier( f2 )
    case HOLOr( f1, f2 )   => !containsQuantifier( f1 ) && !containsQuantifier( f2 )
    case HOLImp( f1, f2 )  => !containsQuantifier( f1 ) && !containsQuantifier( f2 )
    case HOLExVar( v, f )  => isPrenex( f )
    case HOLAllVar( v, f ) => isPrenex( f )
    case _                 => throw new Exception( "ERROR: Unknow operator encountered while checking for prenex formula: " + this )
  }
}

object isAtom {
  def apply( e: HOLExpression ): Boolean = e match {
    case HOLAtom( _, _ ) => true
    case _               => false
  }
}

object subTerms {
  def apply( e: HOLExpression ): List[HOLExpression] = e match {
    case HOLVar( _, _ )            => List( e )
    case HOLConst( _, _ )          => List( e )
    case HOLAtom( _, args )        => e +: args.flatMap( a => subTerms( a ) )
    case HOLFunction( _, args, _ ) => e +: args.flatMap( a => subTerms( a ) )
    case HOLAnd( x, y )            => e +: ( subTerms( x ) ++ subTerms( y ) )
    case HOLOr( x, y )             => e +: ( subTerms( x ) ++ subTerms( y ) )
    case HOLImp( x, y )            => e +: ( subTerms( x ) ++ subTerms( y ) )
    case HOLNeg( x )               => e +: subTerms( x )
    case HOLAllVar( _, x )         => e +: subTerms( x )
    case HOLExVar( _, x )          => e +: subTerms( x )
    case HOLAbs( _, x )            => e +: subTerms( x )
    case HOLApp( x, y )            => e +: ( subTerms( x ) ++ subTerms( y ) )
    case _                         => throw new Exception( "Unrecognized symbol." )
  }
}

object isLogicalSymbol {
  def apply( e: HOLExpression ): Boolean = e match {
    case x: HOLConst => x.sym.isInstanceOf[LogicalSymbolA]
    case _           => false
  }
}

/**
 * the logical complexity of this formula, i.e. the number of logical connectives and atoms
 * starting from the root of the formula. The inner structure of atoms is not counted.
 */
object lcomp {
  def apply( formula: HOLFormula ): Int = formula match {
    case HOLAtom( _, _ )   => 1
    case HOLNeg( f )       => lcomp( f ) + 1
    case HOLAnd( f, g )    => lcomp( f ) + lcomp( g ) + 1
    case HOLOr( f, g )     => lcomp( f ) + lcomp( g ) + 1
    case HOLImp( f, g )    => lcomp( f ) + lcomp( g ) + 1
    case HOLExVar( x, f )  => lcomp( f ) + 1
    case HOLAllVar( x, f ) => lcomp( f ) + 1
  }

  def apply( seq: FSequent ): Int = seq.antecedent.foldLeft( 0 )( _ + lcomp( _ ) ) + seq.succedent.foldLeft( 0 )( _ + lcomp( _ ) )
}

/**
 * Returns the quantifier free part of a prenex formula
 */
object getMatrix {
  /**
   * Strips the outermost block of quantifiers from a formula f in prenex form. The result is also called the
   * matrix of f.
   * @param f the formula of the form Qx1.Qx2. ... .Qxn.F[x1,...xn] where F is quantifier free. (n may be 0)
   * @return the stripped formula F[x1,...,xn]
   */
  def apply( f: HOLFormula ): HOLFormula = {
    assert( isPrenex( f ) )
    f match {
      case HOLVar( _, _ ) |
        HOLConst( _, _ ) |
        HOLAtom( _, _ ) |
        HOLImp( _, _ ) |
        HOLAnd( _, _ ) |
        HOLOr( _, _ ) |
        HOLNeg( _ ) => f
      case HOLExVar( x, f0 )  => getMatrix( f0 )
      case HOLAllVar( x, f0 ) => getMatrix( f0 )
      case _                  => throw new Exception( "ERROR: Unexpected case while extracting the matrix of a formula." )
    }
  }
}

object normalizeFreeVariables {
  /**
   * Systematically renames the free variables by their left-to-right occurence in a HOL Formula f to x_{i} where all
   * x_{i} are different from the names of all bound variables in the term. I.e. reversing the substitution yields
   * the syntactically same formula.
   *
   * @param f the formula to be normalized
   * @return a pair (g,sub) such that g = sub(f). reversing sub allows to restore the original variables.
   */
  def apply( f: HOLFormula ): ( HOLFormula, HOLSubstitution ) = apply( f.asInstanceOf[HOLExpression] ).asInstanceOf[( HOLFormula, HOLSubstitution )]

  /**
   * Systematically renames the free variables by their left-to-right occurence in a HOL Expression f to x_{i} where all
   * x_{i} are different from the names of all bound variables in the term. I.e. reversing the substitution yields
   * the syntactically same formula.
   *
   * @param f the expression to be normalized
   * @return a pair (g,sub) such that g = sub(f). reversing sub allows to restore the original variables.
   */
  def apply( f: HOLExpression ): ( HOLExpression, HOLSubstitution ) = {
    var i = 0
    //generate a blacklist that prevents renaming of bound variables
    val blacklist = getAllPositions2( f ).flatMap( _._2 match {
      case HOLAllVar( x, _ ) => List( x.sym.toString )
      case HOLExVar( x, _ )  => List( x.sym.toString )
      case _                 => Nil
    } )

    apply( f, () => {
      var name = "x_{" + i + "}"
      do {
        i = i + 1;
        name = "x_{" + i + "}"
      } while ( blacklist.contains( name ) )
      name
    } )
  }

  /**
   * Works exactly like normalizeFreeVaribles(f:HOLFormula) but allows the specification of your own name generator.
   * Please note that such a normalized formula is still only unique up to alpha equality. Compare for example
   * (all y P(x,y)) with (all x_{0} P(x,x_{0}):
   * the first normalizes to (all y P(x_{0},y whereas the second normalizes to (all x_{0}1 P(x_{0},x_{0}1).
   *
   * @param f the formula to be normalized
   * @param freshName a function which generates a fresh name every call.
   * @return a pair (g,sub) such that g = sub(f). reversing sub allows to restore the original variables.
   */
  def apply( f: HOLFormula, freshName: () => String ): ( HOLFormula, HOLSubstitution ) =
    apply( f.asInstanceOf[HOLExpression], freshName ).asInstanceOf[( HOLFormula, HOLSubstitution )]

  /**
   * Works exactly like normalizeFreeVaribles(f:HOLExpression) but allows the specification of your own name generator.
   * Please note that such a normalized formula is still only unique up to alpha equality. Compare for example
   * (all y P(x,y)) with (all x_{0} P(x,x_{0}):
   * the first normalizes to (all y P(x_{0},y whereas the second normalizes to (all x_{0}1 P(x_{0},x_{0}1).
   *
   * @param f the formula to be normalized
   * @param freshName a function which generates a fresh name every call.
   * @return a pair (g,sub) such that g = sub(f). reversing sub allows to restore the original variables.
   */
  def apply( f: HOLExpression, freshName: () => String ): ( HOLExpression, HOLSubstitution ) = {
    val vs = freeVariables( f )
    val map = vs.foldLeft( Map[HOLVar, HOLVar]() )( ( map, v ) => {
      if ( map.contains( v ) ) map else {
        val name = freshName()
        map + ( ( v, v.factory.createVar( StringSymbol( name ), v.exptype ).asInstanceOf[HOLVar] ) )
      }
    } )

    val sub = HOLSubstitution( map )
    ( sub( f ), sub )
  }
}

object toAbbreviatedString {
  /**
   * This function takes a HOL construction and converts it to a abbreviated string version. The abbreviated string version is made
   * by replacing the code construction for logic symbols by string versions in the file language/hol/logicSymbols.scala.
   * Several recursive function calls will be transformed into an abbreviated form (e.g. f(f(f(x))) => f^3^(x)).
   * Terms are also handled by the this function.
   * @param  e  The method has no parameters other then the object which is to be written as a string
   * @throws Exception This occurs when an unknown subformula is found when parsing the HOL construction
   * @return A String which contains the defined symbols in language/hol/logicSymbols.scala.
   *
   */
  def apply( e: HOLExpression ): String = {

    val p = pretty( e )

    val r: String = e match {
      case HOLFunction( x, args, tpe ) => {
        if ( p._1 != p._2 && p._2 != "tuple1" )
          if ( p._3 > 0 )
            return p._2 + "^" + ( p._3 + 1 ) + "(" + p._1 + ") : " + tpe.toString()
          else
            return p._1
        else
          return p._1
      }
      case _ => return p._1
    }

    return r
  }

  private def pretty( exp: HOLExpression ): ( String, String, Int ) = {

    val s: ( String, String, Int ) = exp match {
      case null           => ( "null", "null", -2 )
      case HOLVar( x, t ) => ( x.toString() + " : " + t.toString(), x.toString(), 0 )
      case HOLAtom( x, args ) => {
        ( x.toString() + "(" + ( args.foldRight( "" ) {
          case ( x, "" )  => "" + toAbbreviatedString( x )
          case ( x, str ) => toAbbreviatedString( x ) + ", " + str
        } ) + ")" + " : o", x.toString(), 0 )
      }
      case HOLFunction( x, args, t ) => {
        // if only 1 argument is provided
        // check if abbreviating of recursive function calls is possible
        if ( args.length == 1 ) {
          val p = pretty( args.head )

          // current function is equal to first and ONLY argument
          if ( p._2 == x.toString() ) {
            // increment counter and return (<current-string>, <functionsymbol>, <counter>)
            return ( p._1, x.toString(), p._3 + 1 )
          } // function symbol has changed from next to this level
          else {

            // in case of multiple recursive function calls
            if ( p._3 > 0 ) {
              return ( p._2 + "^" + p._3 + "(" + p._1 + ") : " + t.toString(), x.toString(), 0 )
            } // otherwise
            else {
              return ( p._1, x.toString(), 1 )
            }
          }
        } else {
          return ( x.toString() + "(" + ( args.foldRight( "" ) {
            case ( x, "" ) => toAbbreviatedString( x )
            case ( x, s )  => toAbbreviatedString( x ) + ", " + s
          } ) + ") : " + t.toString(), x.toString(), 0 )
        }

      }
      case HOLAnd( x, y )         => ( "(" + toAbbreviatedString( x ) + " " + AndSymbol + " " + toAbbreviatedString( y ) + ")", AndSymbol.toString(), 0 )
      case HOLEquation( x, y )    => ( "(" + toAbbreviatedString( x ) + " " + EqSymbol + " " + toAbbreviatedString( y ) + ")", EqSymbol.toString(), 0 )
      case HOLOr( x, y )          => ( "(" + toAbbreviatedString( x ) + " " + OrSymbol + " " + toAbbreviatedString( y ) + ")", OrSymbol.toString(), 0 )
      case HOLImp( x, y )         => ( "(" + toAbbreviatedString( x ) + " " + ImpSymbol + " " + toAbbreviatedString( y ) + ")", ImpSymbol.toString(), 0 )
      case HOLNeg( x )            => ( NegSymbol + toAbbreviatedString( x ), NegSymbol.toString(), 0 )
      case HOLExVar( x, f )       => ( ExistsSymbol + toAbbreviatedString( x ) + "." + toAbbreviatedString( f ), ExistsSymbol.toString(), 0 )
      case HOLAllVar( x, f )      => ( ForallSymbol + toAbbreviatedString( x ) + "." + toAbbreviatedString( f ), ForallSymbol.toString(), 0 )
      case HOLAbs( v, exp )       => ( "(λ" + toAbbreviatedString( v ) + "." + toAbbreviatedString( exp ) + ")", "λ", 0 )
      case HOLApp( l, r )         => ( "(" + toAbbreviatedString( l ) + ")(" + toAbbreviatedString( r ) + ")", "()()", 0 )
      case HOLConst( x, exptype ) => ( x.toString(), x.toString(), 0 )
      case _                      => throw new Exception( "ERROR: Unknown HOL expression." );
    }
    return s

  }

}
