// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.15"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "tech.rocksavage"
ThisBuild / organizationName := "Rocksavage Technology"

Test / parallelExecution := false

val chiselVersion   = "5.3.0"
val scalafmtVersion = "2.5.0"

lazy val root = (project in file("."))
    .settings(
      name                   := "test_utils",
      Test / publishArtifact := true,
      libraryDependencies ++= Seq(
        "org.chipsalliance" %% "chisel"     % chiselVersion,
        "edu.berkeley.cs"   %% "chiseltest" % "6.0.0",
        "org.rogach"        %% "scallop"    % "5.2.0"
      ),
      scalacOptions ++= Seq(
        "-language:reflectiveCalls",
        "-deprecation",
        "-feature",
        "-Xcheckinit",
        "-Ymacro-annotations"
      ),
      addCompilerPlugin(
        "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
      )
    )

// Scala coverage settings
coverageDataDir            := target.value / "../generated/scalaCoverage"
coverageFailOnMinimum      := false
coverageMinimumStmtTotal   := 90
coverageMinimumBranchTotal := 95
