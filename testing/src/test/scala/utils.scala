package at.logic.testing

import java.io.File

import at.logic.gapt.utils.executionModels.timeout.{ TimeOutException, withTimeout }
import org.specs2.execute.AsResult
import org.specs2.matcher.ThrownExpectations

import scala.concurrent.duration.Duration

object skipIfRunsLongerThan extends ThrownExpectations {
  def apply[T: AsResult]( timeout: Duration )( f: => T ) =
    try {
      val result = withTimeout( timeout toMillis )( f )
      AsResult( result )
    } catch {
      case ex: TimeOutException => skipped( s"Runtime exceeded ${timeout}." )
    }
}

object recursiveListFiles {
  def apply( fn: String ): List[File] = apply( new File( fn ) )

  def apply( f: File ): List[File] =
    if ( f.isDirectory )
      f.listFiles.toList.flatMap( apply )
    else
      List( f )
}
