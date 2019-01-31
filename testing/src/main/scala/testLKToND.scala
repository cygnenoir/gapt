package gapt.testing

import ammonite.ops.FilePath
import gapt.expr.hol.containsQuantifierOnLogicalLevel
import gapt.formats.tptp.TptpParser
import gapt.proofs.context.mutable.MutableContext
import gapt.formats.tptp.TptpImporter
import gapt.proofs.expansion.{ ExpansionProofToLK, deskolemizeET }
import gapt.proofs.lk.transformations.LKToND
import gapt.proofs.lk.util.containsEqualityReasoning
import gapt.proofs.lk.util.isMaeharaMG3i
import gapt.proofs.lk.{ LKProof, OrRightRule, WeakeningRightRule }
import gapt.proofs.nd.{ ExcludedMiddleRule, NDProof }
import gapt.proofs.resolution.{ ResolutionToExpansionProof, structuralCNF }
import gapt.proofs.loadExpansionProof
import gapt.provers.eprover.EProver
import gapt.utils.{ LogHandler, Logger, MetricsPrinter }

private object lkStats {
  def apply( lk: LKProof, logger: Logger ): Unit = {
    import logger._
    metric( "size_lk", lk.treeLike.size )
    metric( "equational", containsEqualityReasoning( lk ) )
    metric( "lk_max_succ_size", lk.subProofs.map( _.endSequent.succedent.size ).max )
    metric( "lk_max_succ_set_size", lk.subProofs.map( _.endSequent.succedent.toSet.size ).max )
    metric( "lk_in_mg3i", isMaeharaMG3i( lk ) )

    metric( "lk_disj_right", lk.subProofs.count( _.isInstanceOf[OrRightRule] ) )
    metric( "lk_disj_right_after_weak", lk.subProofs.count {
      case OrRightRule( WeakeningRightRule( _, _ ), _, _ ) => true
      case _ => false
    } )
  }
}
private object ndStats {
  def apply( nd: NDProof, logger: Logger ): Unit = {
    import logger._
    metric( "size_nd", nd.treeLike.size )

    val emFormulas = nd.treeLike.postOrder.collect {
      case em: ExcludedMiddleRule => em.formulaA
    }
    val qEmFormulas = emFormulas.filter( containsQuantifierOnLogicalLevel( _ ) )

    metric( "num_excl_mid", emFormulas.size )
    metric( "num_quant_excl_mid", qEmFormulas.size )
    metric( "num_excl_mid_distinct", emFormulas.toSet.size )
    metric( "num_quant_excl_mid_distinct", qEmFormulas.toSet.size )
  }
}

object testLKToND extends scala.App {
  val logger = Logger( "testLKToND" )
  import logger._

  val metricsPrinter = new MetricsPrinter
  LogHandler.current.value = metricsPrinter

  try time( "total" ) {

    val Seq( fileName ) = args.toSeq
    metric( "file", fileName )

    val expansion = time( "import" ) { loadExpansionProof( FilePath( fileName ) ) }
    metric( "size_exp", expansion.size )

    val Right( lk ) = time( "exp2lk" ) { ExpansionProofToLK.withIntuitionisticHeuristics( expansion ) }
    lkStats( lk, logger )

    val nd = time( "lk2nd" ) { LKToND( lk ) }
    ndStats( nd, logger )

    metric( "status", "ok" )

  } catch {
    case t: Throwable =>
      metric( "status", "exception" )
      metric( "exception", t.toString )
  }

}

object testLKToND2 extends scala.App {
  val logger = Logger( "testLKToND2" )
  import logger._
  val metricsPrinter = new MetricsPrinter
  LogHandler.current.value = metricsPrinter

  try time( "total" ) {
    val Seq( fileName ) = args.toSeq
    metric( "file", fileName )

    val tptp = time( "tptp" ) { TptpImporter.loadWithIncludes( FilePath( fileName ) ) }
    val problem = tptp.toSequent
    implicit val ctx = MutableContext.guess( problem )
    val cnf = time( "clausifier" ) { structuralCNF( problem ) }
    time( "prover" ) {
      new EProver(
        Seq( "--auto-schedule", "--soft-cpu-limit=120", "--memory-limit=2048" ) ).getResolutionProof( cnf )
    } match {
      case Some( resolution ) =>
        val expansion = time( "res2exp" ) { ResolutionToExpansionProof( resolution ) }
        metric( "size_exp", expansion.size )

        val desk = time( "desk" ) { deskolemizeET( expansion ) }

        time( "exp2lk" ) { ExpansionProofToLK.withIntuitionisticHeuristics( desk ) } match {
          case Right( lk ) =>
            lkStats( lk, logger )

            val nd = time( "lk2nd" ) { LKToND( lk ) }
            ndStats( nd, logger )

            metric( "status", "ok" )

          case Left( _ ) =>
            metric( "status", "desk_wo_eq" )
        }

      case None =>
        metric( "status", "unprovable" )
    }

  } catch {
    case t: Throwable =>
      metric( "status", "exception" )
      metric( "exception", t.toString.take( 100 ) )
  }
}
