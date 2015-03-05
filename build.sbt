import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}

lazy val commonSettings = Seq(
  organization := "at.logic.gapt",
  organizationHomepage := Some(url("https://gapt.github.io/")),
  licenses += ("GNU GPL v3", url("http://www.gnu.org/licenses/gpl.html")),
  startYear := Some(2008),
  version := "1.10-SNAPSHOT",

  scalaVersion := "2.11.6",
  scalacOptions in (Compile, doc) ++= Seq("-diagrams","-implicits"),
  testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
  libraryDependencies ++= testDependencies map(_ % Test),

  // scalaz-stream is not on maven.org
  resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",

  sourcesInBase := false // people like to keep scripts lying around
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  disablePlugins(JUnitXmlReportPlugin).
  settings(
    name := "gapt",
    description := "General Architecture for Proofs",

    mainClass := Some("at.logic.cli.CLIMain"),

    // Release stuff
    test in assembly := {}, // don't execute test when assembling jar
    releaseDist <<= (sbtassembly.AssemblyKeys.assembly, Keys.baseDirectory, Keys.target, Keys.version) map {
        (assemblyJar: File, baseDirectory: File, target: File, version: String) =>
      val archiveFile = target / s"gapt-$version.tar.gz"

      Process(List("latexmk", "-pdf", "user_manual.tex"), baseDirectory / "doc") !

      val filesToIncludeAsIs = List(
        "COPYING", "cli.sh", "gui.sh", "atp.sh", "include.sh", "examples")
      val entries = List((assemblyJar, s"gapt-$version.jar")) ++
        filesToIncludeAsIs.flatMap{fn => recursiveListFiles(baseDirectory / fn)}
          .map{f => (f, baseDirectory.toPath.relativize(f.toPath))} ++
        List((baseDirectory / "doc/README.dist", "README"),
             (baseDirectory / "doc/user_manual.pdf", "user_manual.pdf"))

      val archiveStem = s"gapt-$version"

      IO.gzipFileOut(archiveFile) { gzipOut =>
        val tarOut = new TarArchiveOutputStream(gzipOut)

        entries.foreach { case (file, pathInArchive) =>
          val tarEntry = new TarArchiveEntry(file, s"$archiveStem/$pathInArchive")
          if (file.canExecute) tarEntry.setMode(BigInt("755", 8).toInt)
          tarOut.putArchiveEntry(tarEntry)
          IO.transfer(file, tarOut)
          tarOut.closeArchiveEntry()
        }

        tarOut.close()
      }

      archiveFile
    },

    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
      "org.apache.commons" % "commons-lang3" % "3.3.2",
      "org.slf4j" % "slf4j-api" % "1.7.10",
      "org.slf4j" % "slf4j-log4j12" % "1.7.10",
      "xml-resolver" % "xml-resolver" % "1.2"),

    // UI
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "jline" % "jline" % "2.12.1",
      "org.scala-lang.modules" %% "scala-swing" % "1.0.1",
      "com.itextpdf" % "itextpdf" % "5.5.5",
      "org.scilab.forge" % "jlatexmath" % "1.0.2")
  )

import scalariform.formatter.preferences._
addCommandAlias("format", "; scalariformFormat ; test:scalariformFormat")
defaultScalariformSettings
ScalariformKeys.preferences := FormattingPreferences()
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(SpaceInsideParentheses, true)

lazy val testing = (project in file("testing")).
  dependsOn(root).
  settings(commonSettings: _*).
  disablePlugins(JUnitXmlReportPlugin).
  settings(
    name := "gapt-testing",
    description := "gapt extended regression tests"
  )

lazy val releaseDist = TaskKey[File]("release-dist", "Creates the release tar ball.")

lazy val testDependencies = Seq(
  "junit" % "junit" % "4.12",
  "org.specs2" %% "specs2-core" % "2.4.17",
  "org.specs2" %% "specs2-matcher" % "2.4.17",
  "org.specs2" %% "specs2-mock" % "2.4.17",
  "org.specs2" %% "specs2-junit" % "2.4.17",
  "org.scalacheck" %% "scalacheck" % "1.12.2")

def oneJvmPerTest(tests: Seq[TestDefinition]) =
  tests map { test =>
    new Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(ForkOptions()))
  }

def recursiveListFiles(f: File): List[File] =
  if (f.isDirectory)
    IO.listFiles(f).toList.flatMap(recursiveListFiles)
  else
    List(f)

