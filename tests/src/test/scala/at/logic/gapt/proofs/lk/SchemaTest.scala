package at.logic.gapt.proofs.lk

import at.logic.gapt.expr._
import at.logic.gapt.examples.tautSchema
import at.logic.gapt.examples.NiaSchema
import at.logic.gapt.examples.gniaSchema
import at.logic.gapt.proofs.ceres._
import at.logic.gapt.examples.induction.numbers.pluscomm
import at.logic.gapt.expr.fol.natMaker
import at.logic.gapt.expr.hol.CNFp
import at.logic.gapt.proofs.Context.ProofDefinitions
import at.logic.gapt.provers.escargot.Escargot
import org.specs2.mutable.Specification

/**
 * Test for schema code
 * Created by David M. Cerna on 11.02.17.
 */
class SchemaTest extends Specification {
  {
    import tautSchema.ctx
    "simple schema basecase" in {
      val proof = instantiateProof.Instantiate( le"taut ${natMaker( 0 )}" )
      ctx.check( proof )
      ok
    }

    "simple schema stepcase" in {
      val proof = instantiateProof.Instantiate( le"taut ${natMaker( 1 )}" )
      ctx.check( proof )
      ok
    }
    "simple schema Large" in {
      val proof = instantiateProof.Instantiate( le"taut ${natMaker( 6 )}" )
      ctx.check( proof )
      ok
    }
  }
  {
    import NiaSchema.ctx

    "Nia-schema basecase" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 0 )}" )
      ctx.check( proof )
      ok
    }
    "Nia-schema stepcase" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 1 )}" )
      ctx.check( proof )
      ok
    }

    " Nia-schema Large" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 4 )}" )
      ctx.check( proof )
      ok
    }

    "Nia-schema Super Large" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 12 )}" )
      ctx.check( proof )
      ok
    }

    " Nia-schema Clause Set Extraction  Instance 3" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}" )
      ctx.check( proof )
      val thestruct = StructCreators.extract( proof )( ctx )
      CharacteristicClauseSet( thestruct )

      ok
    }
    " Nia-schema Characteristic Formula Extraction Instance 1" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}" )
      ctx.check( proof )
      val thestruct = StructCreators.extract( proof )( ctx )
      val Form = CharacteristicFormulaN( thestruct )
      subsumedClausesRemoval( CNFp( Form ).toList )
      ok
    }

    " Nia-schema Clause Set Refutation  Instance 1" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 1 )}" )
      ctx.check( proof )
      val thestruct = StructCreators.extract( proof )( ctx )
      val cs = CharacteristicClauseSet( thestruct )
      val refutation = Escargot.getResolutionProof( cs )
      refutation must beSome
    }

    " Extracting the Schematic Characteristic Clause Set of the Niaschema" in {
      SchematicStruct( "omega" )( ctx ) must beSome
      ok
    }
    " Extracting the Schematic Characteristic Clause Set Checking number of symbols" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      SCS.keySet.size must beEqualTo( 6 )
    }

    "Extraction of a Schematic Clause set, size 7 from NiaSchema" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val top = SCS.keySet.foldLeft( ( Var( "", TBase( "nat" ) ), EmptyPlusJunction().asInstanceOf[Struct[Nothing]] ) )( ( x, y ) => {
        val CLS( Apps( Const( name, _ ), vs ), _ ) = y
        if ( name.matches( "omega" ) && !vs.head.equals( natMaker( 0 ) ) ) ( freeVariables( vs.head ).head, y )
        else x
      } )
      InstanceOfSchematicStruct( top._2, SCS, Substitution( top._1.asInstanceOf[Var], natMaker( 7 ) ) )( ctx )
      ok
    }
    "Schematic Clause set equivalent to non schematic" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val top = SCS.keySet.foldLeft( ( Var( "", TBase( "nat" ) ), EmptyPlusJunction().asInstanceOf[Struct[Nothing]] ) )( ( x, y ) => {
        val CLS( Apps( Const( name, _ ), vs ), _ ) = y
        if ( name.matches( "omega" ) && !vs.head.equals( natMaker( 0 ) ) ) ( freeVariables( vs.head ).head, y )
        else x
      } )
      val st = InstanceOfSchematicStruct( top._2, SCS, Substitution( top._1.asInstanceOf[Var], natMaker( 3 ) ) )( ctx )
      val Sclauseset = subsumedClausesRemoval( CharacteristicClauseSet( st ).toList )
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}" )
      val thestruct = StructCreators.extract( proof )( ctx )
      val nonclauseset = subsumedClausesRemoval( CharacteristicClauseSet( thestruct ).toList )
      val fin = ( Sclauseset.forall( s => nonclauseset.exists( clauseSubsumption( _, s ).isDefined ) ) ||
        nonclauseset.forall( s => Sclauseset.exists( clauseSubsumption( _, s ).isDefined ) ) ) && nonclauseset.size == Sclauseset.size
      fin must beEqualTo( true )
    }
    "Schematic Clause set equivalent to Characteristic formula Clause Set" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val top = SCS.keySet.foldLeft( ( Var( "", TBase( "nat" ) ), EmptyPlusJunction().asInstanceOf[Struct[Nothing]] ) )( ( x, y ) => {
        val CLS( Apps( Const( name, _ ), vs ), _ ) = y
        if ( name.matches( "omega" ) && !vs.head.equals( natMaker( 0 ) ) ) ( freeVariables( vs.head ).head, y )
        else x
      } )
      val st = InstanceOfSchematicStruct( top._2, SCS, Substitution( top._1.asInstanceOf[Var], natMaker( 3 ) ) )( ctx )
      val Sclauseset = subsumedClausesRemoval( CharacteristicClauseSet( st ).toList )
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}" )
      val thestruct = StructCreators.extract( proof )( ctx )
      val nonclauseset = subsumedClausesRemoval( CNFp( CharacteristicFormulaN( thestruct ) ).toList )
      val fin = ( Sclauseset.forall( s => nonclauseset.exists( clauseSubsumption( _, s ).isDefined ) ) ||
        nonclauseset.forall( s => Sclauseset.exists( clauseSubsumption( _, s ).isDefined ) ) ) && nonclauseset.size == Sclauseset.size
      fin must beEqualTo( true )
    }
    "Schematic Formula Construction" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val SchemForm = RecursiveCharForm( SCS )
      SCS.size must beEqualTo( SchemForm.size )
    }
  }
  {
    import gniaSchema.ctx

    "gNia-schema both parameters zero" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 0 )} ${natMaker( 0 )}" )
      ctx.check( proof )
      ok
    }
    "gNia-schema first parameter zero" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 0 )} ${natMaker( 5 )}" )
      ctx.check( proof )
      ok
    }
    "gNia-schema second parameter zero" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 5 )} ${natMaker( 0 )}" )
      ctx.check( proof )
      ok
    }
    "gNia-schema both parameters non-zero" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 5 )} ${natMaker( 5 )}" )
      ctx.check( proof )
      ok
    }
    "gNia-schema both parameters non-zero large" in {
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 12 )} ${natMaker( 12 )}" )
      ctx.check( proof )
      ok
    }

    "Test if PlusComm induction proof is K-simple" in {
      IsKSimple( pluscomm ) must_== false
    }

    "Test if K-simple PlusComm induction proof is K-simple" in {
      val result: LKProof = {
        val proofs = pluscomm.subProofs.toList.foldRight( List[LKProof]() )( ( a, z ) => {
          a match {
            case p: InductionRule =>
              val succ: Var = p.cases.foldRight( Var( "wrong", p.indTy ): Var )( ( a, z ) => {
                a match {
                  case InductionCase( _, Const( "s", _ ), _, e, _ ) => e.head
                  case _ => z
                }
              } )
              val ret: Expr = Substitution( freeVariables( p.formula.term ).head -> succ )( p.formula.term )
              InductionRule( p.cases, Abs( succ, ret ), succ ) :: z
            case _ => z
          }
        } )
        if ( proofs.nonEmpty ) {
          if ( proofs.tail.nonEmpty ) {
            val nonq = proofs.tail.foldRight( ( proofs.head, proofs.head.mainFormulas.head ) )(
              ( a, z ) => {
                val newp = AndRightRule( z._1, z._1.conclusion.indexOfInSuc( z._2 ), a, a.conclusion.indexOfInSuc( a.mainFormulas.head ) )
                ( newp, newp.mainFormula )
              } )._1
            val InductionRule( _, _, x: Var ) = proofs.head
            ForallRightRule( nonq, nonq.mainIndices.head, x, Var( "x", x.ty ) )
          } else proofs.head
        } else pluscomm
      }
      IsKSimple( result ) must_== true
    }
    "Schematic Clause set equivalent to non schematic" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val top = SCS.keySet.foldLeft( ( ( Var( "", TBase( "nat" ) ), Var( "", TBase( "nat" ) ) ), EmptyPlusJunction().asInstanceOf[Struct[Nothing]] ) )( ( x, y ) => {
        val CLS( Apps( Const( name, _ ), vs ), _ ) = y
        if ( name.matches( "omega" ) && !vs.head.equals( natMaker( 0 ) ) ) ( ( freeVariables( vs.head ).head, freeVariables( vs.tail.head ).head ), y )
        else x
      } )
      val theStructWeNeed = InstanceOfSchematicStruct( top._2, SCS, Substitution( top._1._1, natMaker( 3 ) ).compose( Substitution( top._1._2, natMaker( 3 ) ) ) )( ctx )
      val SClauseSet = subsumedClausesRemoval( CharacteristicClauseSet( theStructWeNeed ).toList )
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}  ${natMaker( 3 )}" )
      val theStruct = StructCreators.extract( proof )( ctx )
      val nonClauseSet = subsumedClausesRemoval( CharacteristicClauseSet( theStruct ).toList )
      val fin = ( SClauseSet.forall( s => nonClauseSet.exists( clauseSubsumption( _, s ).isDefined ) ) ||
        nonClauseSet.forall( s => SClauseSet.exists( clauseSubsumption( _, s ).isDefined ) ) ) && nonClauseSet.size == SClauseSet.size
      fin must beEqualTo( true )
    }

    "Schematic Clause set equivalent to Characteristic formula Clause Set" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val top = SCS.keySet.foldLeft( ( ( Var( "", TBase( "nat" ) ), Var( "", TBase( "nat" ) ) ), EmptyPlusJunction().asInstanceOf[Struct[Nothing]] ) )( ( x, y ) => {
        val CLS( Apps( Const( name, _ ), vs ), _ ) = y
        if ( name.matches( "omega" ) && !vs.head.equals( natMaker( 0 ) ) ) ( ( freeVariables( vs.head ).head, freeVariables( vs.tail.head ).head ), y )
        else x
      } )
      val theStructWeNeed = InstanceOfSchematicStruct( top._2, SCS, Substitution( top._1._1, natMaker( 3 ) ).compose( Substitution( top._1._2, natMaker( 3 ) ) ) )( ctx )
      val SClauseSet = subsumedClausesRemoval( CharacteristicClauseSet( theStructWeNeed ).toList )
      val proof = instantiateProof.Instantiate( le"omega ${natMaker( 3 )}  ${natMaker( 3 )}" )
      val theStruct = StructCreators.extract( proof )( ctx )
      val nonClauseSet = subsumedClausesRemoval( CNFp( CharacteristicFormulaN( theStruct ) ).toList )
      val fin = ( SClauseSet.forall( s => nonClauseSet.exists( clauseSubsumption( _, s ).isDefined ) ) ||
        nonClauseSet.forall( s => SClauseSet.exists( clauseSubsumption( _, s ).isDefined ) ) ) && nonClauseSet.size == SClauseSet.size
      fin must beEqualTo( true )
    }
    "Schematic Formula Construction" in {
      val SCS = SchematicStruct( "omega" )( ctx ).getOrElse( Map() )
      val SchemForm = RecursiveCharForm( SCS )
      SCS.size must beEqualTo( SchemForm.size )
    }
  }

  {
    import at.logic.gapt.examples.induction.numbers.ctx
    "Constructing schematic pluscomm proof" in {
      val ccon = ctx.newMutable
      ArithmeticInductionToSchema( pluscomm, Const( "Commutativity", TBase( "nat" ) ) )( ccon )
      val res = ccon.get[ProofDefinitions].components.keySet.map( x => ccon.get[ProofDefinitions].components.getOrElse( x, Set() ) ).foldLeft( 0 )( ( x, y ) => x + y.size )
      res must_== 10
    }

    "Instantiating schematic pluscomm proof" in {
      val ccon = ctx.newMutable
      ArithmeticInductionToSchema( pluscomm, Const( "Commutativity", TBase( "nat" ) ) )( ccon )
      val P = hoc"P: nat>nat"
      instantiateProof.Instantiate( le"$P ${natMaker( 10 )}" )( ccon )
      ok
    }
  }
}

