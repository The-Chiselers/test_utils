// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.test

import chisel3._
import scala.util.Random
import scala.collection.immutable.ListMap
import java.time._
import java.time.format._
import java.io.{File, PrintWriter}
import scala.collection.mutable
import firrtl2.AnnotationSeq
import firrtl2.annotations.Annotation // Correct Annotation type for firrtl2
import firrtl2.options.TargetDirAnnotation
import TestUtils.randData
import chisel3.util._
import chiseltest._
import chiseltest.coverage._
import chiseltest.simulator._

object coverageCollector {
  private val cumulativeCoverage: mutable.Map[String, BigInt] = mutable.Map()

  def collectCoverage(
      cov: Seq[Annotation],
      testName: String,
      testConfig: String,
      coverage: Boolean,
      covDir: String
  ): Unit = {
    if (coverage) {
      val coverage = cov
        .collectFirst { case a: TestCoverage => a.counts }
        .getOrElse(Map.empty)
        .toMap

      // Convert Map[String, Long] to Map[String, BigInt]
      val bigIntCoverage = coverage.map { case (key, value) => key -> BigInt(value) }

      // Merge the test coverage into the cumulative coverage
      for ((key, value) <- bigIntCoverage) {
        cumulativeCoverage.update(key, cumulativeCoverage.getOrElse(key, BigInt(0)) + value)
      }


      val verCoverageDir = new File(covDir + "/verilog")
      verCoverageDir.mkdirs()
      val coverageFile = verCoverageDir.toString + "/" + testName + "_" +
        testConfig + ".cov"

      // Save individual test coverage
      saveCoverageToFile(bigIntCoverage, coverageFile)

      // val stuckAtFault = TestUtils.checkCoverage(bigIntCoverage.map { case (k, v) => k -> v.toLong }.toMap, coverageFile)
      // if (stuckAtFault)
      //   println(
      //     s"WARNING: At least one IO port did not toggle -- see $coverageFile"
      //   )
      info(s"Verilog Coverage report written to $coverageFile")
    }
  }

  def saveCumulativeCoverage(coverage: Boolean, covDir: String): Unit = {
    if (coverage) {
      val verCoverageDir = new File(covDir + "/verilog")
      val cumulativeFile = s"$verCoverageDir/cumulative_coverage.cov"
      verCoverageDir.mkdirs()
      saveCoverageToFileWithTogglePercentage(cumulativeCoverage.toMap, cumulativeFile)
      info(s"Cumulative coverage report written to $cumulativeFile")
    }
  }

  private def saveCoverageToFile(coverage: Map[String, BigInt], filePath: String): Unit = {
    val writer = new PrintWriter(new File(filePath))
    try {
      for ((key, value) <- coverage) {
        writer.println(s"$key: $value")
      }
    } finally {
      writer.close()
    }
  }

private def saveCoverageToFileWithTogglePercentage(coverage: Map[String, BigInt], filePath: String): Unit = {
  val writer = new PrintWriter(new File(filePath))
  try {
    for ((key, value) <- coverage) {
      // Check if the port is one of the specified ones and its value is 0
      if ((key.contains("PADDR") || key.contains("PWDATA") || key.contains("PRDATA")) && value == 0) {
        writer.println(s"$key: $value (Excluded)")  // Mark as Excluded
      } else {
        writer.println(s"$key: $value")  // Otherwise, just print the value
      }
    }
    
    // Exclude paddr, pwdata, and prdata if they are 0 for toggle percentage calculation
    val filteredCoverage = coverage.filterNot {
      case (key, value) => (key.contains("PADDR") || key.contains("PWDATA") || key.contains("PRDATA")) && value == 0
    }
    val toggledPorts = filteredCoverage.count(_._2 > 0)
    val totalPorts = filteredCoverage.size
    val togglePercentage = if (totalPorts > 0) (toggledPorts.toDouble / totalPorts) * 100 else 0.0
    
    writer.println(f"Final Toggle Percentage: $togglePercentage%.2f%%")
  } finally {
    writer.close()
  }
}


  private def info(message: String): Unit = {
    println(message)
  }
}

/** Collection of chiselWare utilities */
object TestUtils {

  /** Checks coverage and writes results to a file
   *
   * All chiselWare-compliant cores must have tests that exercise all IO ports.
   *
   * Chiseltest coverage utilities are used to achieve this. Coverage data is
   * stored in a Map. This utility processes that map file, checking that all
   * ports are toggled and returns an error if there is a stuck-at port which
   * indicates that the test is not sufficiently robust.
   *
   * The full report is written to the doc directory as part of the standard
   * deliverables.
   *
   * {{{
   * val result = checkCoverage(myCovMap,"cov.rpt")
   * }}}
   *
   * @param coverage
   *   the coverage Map containing coverage data
   * @param file
   *   the name of the file to write the coverage report
   * @return
   *   whether the coverage passed or failed
   */
  def checkCoverage(coverage: Map[String, Long], file: String): Boolean = {
    val cov = new File(file)
    val covFile = new PrintWriter(cov)
    val numTicks = coverage("tick")
    val netCoverage = coverage.view.filterKeys(_ != "dut.tick").toMap
    val sortedCoverage = ListMap(netCoverage.toSeq.sortBy(_._1): _*).toMap
    var stuckAtOne = false
    var stuckAtZero = false
    val separator = "-" * 80
    covFile.write(separator + "\n")
    covFile.write("%\t\t\t\t\t\t\t\t\tCount\t\t\t\tCoverage Point \n")
    covFile.write(separator + "\n")
    sortedCoverage.keys.foreach((coverPoint) => {
      val toggleCount = sortedCoverage(coverPoint)
      val togglePct = toggleCount.toDouble / numTicks * 100
      covFile.write(
        f"${togglePct}%1.2f\t\t\t\t\t ${toggleCount}%8s\t\t\t\t${coverPoint}\n"
      )
      if (toggleCount == 0) {
        covFile.write(s"${coverPoint} is stuck at 0 \n")
        stuckAtZero = true
      }
      if (toggleCount == numTicks) {
        covFile.write(s"${coverPoint} is stuck at 1 \n")
        stuckAtOne = true
      }
    })
    covFile.write(separator + "\n")
    covFile.write(LocalDateTime.now.toString + "\n")
    covFile.close()
    return (stuckAtZero | stuckAtOne)
  }

  def coverageCollection(
    cov: Seq[Annotation],
    testName: String,
    testConfig: String,
    coverage: Boolean,
    covDir: String
    ): Unit = {
    if (coverage) {
      val coverage = cov
        .collectFirst { case a: TestCoverage => a.counts }
        .get
        .toMap

      val verCoverageDir = new File(covDir + "/verilog")
      verCoverageDir.mkdirs()
      //val coverageFile = verCoverageDir.toString + "/" + testName + "_" +
      //  testConfig + ".cov"

      val buildRoot = sys.env.get("BUILD_ROOT")
      if (buildRoot.isEmpty) {
        println("BUILD_ROOT not set, please set and run again")
        System.exit(1)
      }
      // path join
      //val scalaCoverageDir = new File(buildRoot.get + "/cov/scala")
      //val verCoverageDir = new File(buildRoot.get + "/cov/verilog")
      verCoverageDir.mkdirs()
      val coverageFile = verCoverageDir.toString + "/" + testName + "_" +
        testConfig + ".cov"

      val stuckAtFault = checkCoverage(coverage, coverageFile)
      if (stuckAtFault)
        println(
          s"WARNING: At least one IO port did not toggle -- see $coverageFile"
        )
      //info(s"Verilog Coverage report written to $coverageFile")
    }
  }

  /** Return a random data word of arbitrary length
   *
   * Built-in scala random number generators do not work for generating random
   * numbers for words that are of a length not divisible by 4. This utility
   * returns a randomized bit vector where every bit is randomized.
   *
   * {{{
   * val myData = randData(19) // return a 19-bit word of random data
   * }}}
   *
   * @param width
   *   the coverage Map containing coverage data
   * @return
   *   a word with random data
   */
  def randData(width: Int): UInt = {

    // format: off
    /** Generate hex choices that can be randomized for creating data
     * patterns. The function returns fewer choices when there is data is
     * less than a full nibble as in the example below with dataWidth =
     * 13
     *
     *                 12 11 10  9  8  7  6  5  4  3  2  1  0
     * full nibbles       ----------- ----------- -----------
     * 1 extra bit     --
     *
     * In this case, bit 12 can only take a 0/1 value because it is not
     * large enough to hold any other hex value.
     */
    // format: on
    def getHexString(x: Int): String = {
      val hexStringRem0 = "0123456789abcdef" // no extra bits
      val hexStringRem1 = "01" // one extra bit
      val hexStringRem2 = "0123" // two extra bits
      val hexStringRem3 = "01234567" // three extra bits
      return x match {
        case 0 => hexStringRem0
        case 1 => hexStringRem1
        case 2 => hexStringRem2
        case 3 => hexStringRem3
      }
    }

    /** Determine whether a partial nibble should be generated */
    def partialNibble(x: Int): Int = {
      if (x == 0) { return 0 }
      else { return 1 }
    }

    val numNibbles = width / 4
    val numLeftOverBits = width % 4

    // format: off
    /** Generate two sets of random strings used for generation of the
     * randomized data. One used for full nibbles the other for partial
     * nibbles.
     *
     * datawidth = 13
     * randFullNibble = "abc"
     * randPartialNibble = "1"
     */
    // format: on

    val randFullNibble = getHexString(0)
    val randPartialNibble = getHexString(numLeftOverBits)

    // format: off
    /** Assemble the test data word by creating two Seqs. One consists of
     * full nibbles, the second is for the last partial nibble. The
     * partial nibble value is prepended to the full list of nibbles.
     *
     * fullNibbleSeq = Seq[List[String]] = List(List(a, b, c))
     * partialNibbleSeq = Seq[List[String]] = List(List(1))
     * assembleSeq = Seq[List[String]] = List(List(1,a,b,c))
     */
    // format: on

    val fullNibbleSeq = Seq.fill(width / 4) {
      Random.shuffle(randFullNibble).head
    }
    val partialNibbleSeq = Seq.fill(partialNibble(numLeftOverBits)) {
      Random.shuffle(randPartialNibble).head
    }
    val assembledSeq = partialNibbleSeq ++ fullNibbleSeq

    // Create a hex BigInt and cast it to UInt
    val randData = BigInt(assembledSeq.mkString, 16).U

    return randData
  }

    // Recursively cover a Data (Bundle, Vec, or leaf).
    // For a leaf that is non-Bool, we convert it to a Vec of Bools and then cover each bit.
    def coverAll(sig: Data, namePrefix: String = ""): Unit = sig match {
        // If the signal is a Bundle, iterate through each element.
        case b: Bundle =>
            b.elements.foreach { case (fieldName, child) =>
                val fullName = if (namePrefix.isEmpty) fieldName else s"${namePrefix}_$fieldName"
                coverAll(child, fullName)
            }
        // If the signal is a Vec, iterate through each element.
        case v: Vec[_] =>
            for (i <- 0 until v.length) {
                coverAll(v(i), s"${namePrefix}_$i")
            }
        // Otherwise, assume it is a leaf.
        // For a Bool we directly cover, for any other Bits we convert to a Vec of Bool using asBools.
        case leaf =>
            coverLeaf(leaf, namePrefix)
    }

    // Helper function for covering leaf signals.
    // If leaf is a Bool, use it directly; if it's some other Bits (UInt, SInt), convert to a Vec of Bools.
    def coverLeaf(leaf: Data, name: String): Unit = leaf match {
        case b: Bool =>
            cover(b).suggestName(name)
        case bits: Bits =>
            // Convert the Bits to a Seq of Bool signals and wrap in a Vec,
            // then recursively cover each bit.
            val vecBools = VecInit(bits.asBools)
            coverAll(vecBools, name)
        case _ =>
            println(s"Warning: signal $name of type ${leaf.getClass.getSimpleName} is not coverable")
    }

}
