import scala.collection.mutable.ArrayBuffer

// Generate `verilatorEmulator` object from the `passed.txt` path.
// A valid passedFile path should be like: /path/to/v1024l8b2-test/debug/passed.txt.
//
// @param passedFile Path to the passed.txt file
def genRunTask(passedFiles: Seq[os.Path]): Seq[String] = {
  passedFiles.flatMap(file => {
    println(s"Generate tests from file: $file")
    val Seq(_, runType, verilatorType) = file.segments.toSeq.reverse.slice(0, 3)
    os.read.lines(file)
      .map(test => s"verilatorEmulator[$verilatorType,$test,$runType].run")
  })
}

// Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
//
// @param allTests The original Seq
// @param bucketSize Specify the size of the output Seq
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] = 
  scala.util.Random.shuffle(alltests).grouped(
    math.ceil(alltests.size.toDouble / bucketSize).toInt
  ).toSeq.map(_.mkString(";"))

class BucketBuffer() {
  private var _buffer: ArrayBuffer[String] = new ArrayBuffer()
  private var _total_cycles = 0;

  def push_back(test: String, cycle: Int) = {
    _buffer += test
    _total_cycles += cycle
  }

  def total_cycles: Int = _total_cycles

  def mkString(): String = {
    _buffer.toArray.mkString(";")
  }
}

// This function will use the greedy partition method to partition the given tests into given `bucketSize` of subset.
// Tests are grouped by their corresponding instruction cycle specified in `cycleDataPath` so that they have a similar time span to execute.
//
// @param allTests List of the test target
// @param cycleDataPath Path to the cycle data
// @param bucketSize Specify the size of the output Seq
def scheduledBuckets(allTests: Seq[String], cycleDataPath: os.Path, bucketSize: Int): Seq[String] = {
  val cycleData = ujson.read(os.read(cycleDataPath))
  val tests = allTests.map(test => {
    // TODO: Remove the fallback cycle number after FP tests are fixed.
    val cycle = cycleData.obj.getOrElse(test, ujson.Num(10000.0)).num.toInt
    (test, cycle)
  })
  // Initialize a list of buckets
  val cargo = (0 until bucketSize).map(_ => new BucketBuffer())
  tests.sortBy(_._2)(Ordering[Int].reverse) // sort by cycle in descending order
    .foreach((elem) => {
      val (testName, cycle) = elem; 
      cargo.minBy(_.total_cycles).push_back(testName, cycle)
    })
  cargo.map(_.mkString).toSeq
}

// Turn Seq( "A,B", "C,D" ) to GitHub Action matrix style json: { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
//
// @param buckets Seq of String that is already packed into bucket using the `buckets` function
// @param outputFile Path to the output json file
def writeJson(buckets: Seq[String], outputFile: os.Path) = 
  os.write.over(outputFile, ujson.Obj("include" -> 
    buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

// Generate a list of grouped tests from the given `defaultPassed` file.
@main
def passedJson(bucketSize: Int, defaultPassed: os.Path, cycleDataPath: os.Path, outputFile: os.Path) =
  writeJson(
    scheduledBuckets(
      genRunTask(
        os.read.lines(defaultPassed).map(defaultPassed / os.up / os.RelPath(_))
      ),
      cycleDataPath,
      bucketSize
    ),
    outputFile
  )

// Resolve all the executable test and filter out unpassed tests
@main
def unpassedJson(
  bucketSize: Int,
  root: os.Path,
  defaultPassed: os.Path,
  outputFile: os.Path
) = {
  val passedFiles = os.read.lines(defaultPassed).map(os.RelPath(_))
  val allTests = passedFiles.map(_.segments.toSeq).flatMap {
    case Seq(emulator, runCfg, _) =>
      os.proc("mill", "resolve", s"verilatorEmulator[$emulator,_,$runCfg].run")
        .call(root).out.text
        .split("\n")
  }
  val passed = genRunTask(passedFiles.map(defaultPassed / os.up / _))
  writeJson(
    buckets(
      (allTests.toSet -- passed.toSet).toSeq,
      bucketSize
    ),
    outputFile
  )
}

// Find the perf.txt file for tests specified in the .github/passed/*/*/perf-cases.txt file,
// and convert them into markdown format.
@main
def convertPerfToMD(root: os.Path) = os
  .walk(root / ".github" / "passed")
  .filter(_.last == "perf-cases.txt")
  .foreach(p => {
    val perfCases = os.read.lines(p).filter(_.length > 0)
    val Seq(emulator, runCfg, _) =
      p.relativeTo(root / ".github" / "passed").segments.toSeq
    val existPerfFile = perfCases
      .filter {testcase =>
        val path = root / os.RelPath(
          s"out/verilatorEmulator/$emulator/$testcase/$runCfg/run.dest/perf.txt"
        )
        os.exists(path)
      }
      .foreach(testcase => {
        val path = root / os.RelPath(
          s"out/verilatorEmulator/$emulator/$testcase/$runCfg/run.dest/perf.txt"
        )
        val output =
          s"""### $testcase
            |
            |* Emulator: $emulator
            |* Run Config: $runCfg
            |
            |```
            |${os.read(path).trim}
            |```
            |\n""".stripMargin
        os.write(root / s"perf-result-$testcase-$emulator-$runCfg.md", output)
      })
  })


def updPerfResult(root: os.Path, task: String, resultOutput: os.Path) = {
  val isEmulatorTask = raw"verilatorEmulator\[([^,]+),([^,]+),([^,]+)\].run".r
  task match {
    case isEmulatorTask(e, t, r) => {
      val passedFile = root / os.RelPath(s".github/passed/$e/$r/passed.json")
      val original = ujson.read(os.read(passedFile))
      val new_cycle = os.read.lines(root / os.RelPath(s"out/verilatorEmulator/$e/$t/$r/run.dest/perf.txt"))
        .apply(0)
        .stripPrefix("total_cycles: ")
        .trim.toInt
      val old_cycle = original.obj(t).num.toInt
      if (old_cycle != new_cycle) {
        os.write.append(resultOutput, s"* $t: $old_cycle -> $new_cycle\n")
        original(t) = new_cycle
        os.write.over(passedFile, ujson.write(original, indent = 2))
      }
    }
  }
}


// This function will split the given jobs with semicolon,
// and attempt to use mill to execute them all.
// If the execution doesn't end successfully, the stdout and sterr will be writed into the loggingDir/fail directory.
//
// ```bash
// # Each test is in the form of `verilatorEmulator[$emulator,$testname,$runcfg]`
// amm .github/scripts/ci.sc $PWD "test1;test2;test3"
// ```
//
// @param: root Path to the project root
// @param: jobs A list of test case concat with semicolon, eg. "taskA;taskB"
// @param: loggingDir Path to the loggin directory. Optional, `root/test-log` by default.
@main
def runTest(root: os.Path, jobs: String, loggingDir: Option[os.Path]) = {
  var logDir = loggingDir.getOrElse(root / "test-log")
  os.remove.all(logDir)
  os.makeDir.all(logDir)
  os.makeDir.all(logDir / "fail")
  val totalJobs = jobs.split(";")
  os.write.over(logDir / "result.md", "## Cycle Changed\n")
  val failed = totalJobs.zipWithIndex.foldLeft(Seq[String]()) {
    case(failed, (job, i)) => {
      val logPath = logDir / s"$job.log"
      println(s"[${i+1}/${totalJobs.length}] Running test case $job")
      val handle = os
        .proc("mill", "--no-server", job)
        .call(cwd=root,
          check=false,
          stdout=logPath,
          mergeErrIntoOut=true)
      if (handle.exitCode > 0) {
        val trimmedOutput = os.proc("tail", "-n", "100", logPath).call().out.text
        os.write(logDir / "fail" / s"$job.log", trimmedOutput)
        failed :+ job
      } else {
        updPerfResult(root, job, logDir / "result.md")
        failed
      }
    }
  }

  if (failed.length > 0) {
    os.write.append(logDir/"result.md", 
      s"""## Failed Tests
        |${failed.map(t => s"* $t").mkString("\n")}
        |""".stripMargin)
    println(s"${failed.length} tests failed:\n${failed.mkString("\n")}")
    throw new Exception("Tests failed")
  } else {
    println(s"All tests passed")
  }
}


// This target will try to build all the test cases in the given testSrcDir and group them into the given output directory.
//
// ```bash
// # Run this in the project root
// amm .github/scripts/ci.sc $PWD/tests ./test-out
// ```
//
// @param testSrcDir Path to the tests directory
// @param outDir Path to the output directory
@main
def buildAllTestCase(testSrcDir: os.Path, outDir: os.Path) = {
  os.remove.all(outDir)
  os.remove.all(testSrcDir / "out")
  os.makeDir.all(outDir)
  val outConfigDir = outDir / "configs"
  os.makeDir.all(outConfigDir)
  val outElfDir = outDir / "cases"
  os.makeDir.all(outElfDir)

  val rawJson = os.proc("mill", "--no-server", "-j", "0", "--silent", "show", "_[_].elf")
      .call(testSrcDir).out.text
      .split('\n')
      // Output provided by -j0 that can't be turn off. It looks like [#00] or [#0] based on $(nproc)
      .map(raw"^\[#\d+\]".r.unanchored.replaceFirstIn(_, "").trim)
      .mkString("");
  // Array[String] => Array[os.Path] A list of "ref:id:path", we need the path only.
  ujson.read(rawJson).arr
    .map(rawPathRef => os.Path(rawPathRef.str.split(":")(2)))
    .foreach(elfPath => {
      val IndexedSeq(module, name) = elfPath.relativeTo(testSrcDir / "out").segments.slice(0, 2)
      val configPath = testSrcDir / "configs" / s"$name-$module.json"
      val origConfig = ujson.read(os.read(configPath))
      origConfig("elf") = ujson.Obj("path" -> s"cases/$module/${elfPath.last}")

      os.makeDir.all(outElfDir / module)
      os.move(elfPath, outElfDir / module / elfPath.last)
      os.write(outConfigDir / s"$name-$module.json", ujson.write(origConfig))
    })
}