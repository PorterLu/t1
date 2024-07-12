// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.configgen

import chisel3.experimental.SerializableModuleGenerator
import chisel3.util.{BitPat, log2Ceil}
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.T1CustomInstruction
import org.chipsalliance.t1.rtl.lsu.LSUInstantiateParameter
import org.chipsalliance.t1.rtl.vrf.RamType

import java.util.LinkedHashMap

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }
  implicit class EmitVParameter(p: T1Parameter) {
    def emit(targetFile: os.Path) = os.write(
      targetFile,
      upickle.default.write(SerializableModuleGenerator(classOf[T1], p), indent = 2)
    )
  }

  @main def listConfigs(
    @arg(name = "project-dir", short = 't') projectDir: os.Path = os.pwd
  ): Unit = {
    val declaredMethods =
      Main.getClass().getDeclaredMethods().filter(m => m.getParameterTypes().mkString(", ") == "class os.Path, boolean")

    import scala.io.AnsiColor._

    declaredMethods.foreach(configgen => {
      val param = configgen.invoke(Main, os.root / "dev" / "null", false)
      println(s"""${BOLD}${MAGENTA_B} ${configgen.getName()} ${RESET}
                 |   ${param.toString()}""".stripMargin)
    })
  }

  @main def updateConfigs(
    @arg(name = "project-dir", short = 't') projectDir: os.Path = os.pwd
  ): Unit = {
    val declaredMethods =
      Main.getClass().getDeclaredMethods().filter(m => m.getParameterTypes().mkString(", ") == "class os.Path, boolean")

    import scala.io.AnsiColor._

    val generatedDir = projectDir / "configgen" / "generated"
    os.list(generatedDir).foreach(f => os.remove(f))

    declaredMethods.foreach(configgen => {
      val configName = configgen.getName()
      configgen.invoke(Main, generatedDir / s"$configName.json", true)
    })
  }

  // DLEN256 VLEN256;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8
  @main def blastoise(
    @arg(name = "target-file", short = 't') targetFile:             os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen = 512
    val dLen = 256
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32f"),
      t1customInstructions = Nil,
      lsuBankParameters =
        // scalar bank 0-1G
        Seq(
          BitSet(BitPat("b00??????????????????????????????"))
        ).map(bs => LSUBankParameter("scalar", bs, 8, true)) ++
          // ddr bank 1G-3G 512M/bank
          Seq(
            BitSet(BitPat("b01???????????????????????00?????"), BitPat("b10???????????????????????00?????")),
            BitSet(BitPat("b01???????????????????????01?????"), BitPat("b10???????????????????????01?????")),
            BitSet(BitPat("b01???????????????????????10?????"), BitPat("b10???????????????????????10?????")),
            BitSet(BitPat("b01???????????????????????11?????"), BitPat("b10???????????????????????11?????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"ddrBank$idx", bs, 8, false) } ++
          // sRam bank 3G+ 256K/bank, 8banks
          Seq(
            BitSet(BitPat("b11000000000?????????????000?????")),
            BitSet(BitPat("b11000000000?????????????001?????")),
            BitSet(BitPat("b11000000000?????????????010?????")),
            BitSet(BitPat("b11000000000?????????????011?????")),
            BitSet(BitPat("b11000000000?????????????100?????")),
            BitSet(BitPat("b11000000000?????????????101?????")),
            BitSet(BitPat("b11000000000?????????????110?????")),
            BitSet(BitPat("b11000000000?????????????111?????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"sramBank$idx", bs, 8, false) },
      vrfBankSize = 1,
      vrfRamType = RamType.p0rwp1rw,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(),
        divfpModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
        otherModuleParameters =
          Seq((
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3))),
        floatModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
        zvbbModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneZvbb], LaneZvbbParam(32, 3)), Seq(0, 1, 2, 3)))
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  // DLEN512 VLEN1K ; NOFP; VRF p0r,p1w   bank2; LSU bank8  beatbyte 16
  @main def machamp(
    @arg(name = "target-file", short = 't') targetFile:             os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen = 1024
    val dLen = 512
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32x"),
      t1customInstructions = Nil,
      // banks=8 dLen=512 beatbyte16
      lsuBankParameters =
        // scalar bank 0-1G
        Seq(
          BitSet(BitPat("b00??????????????????????????????"))
        ).map(bs => LSUBankParameter("scalar", bs, 8, true)) ++
          // ddr bank 1G-3G 512M/bank
          // bp       '01???????????????????????00?????'
          // base     '01000000000000000000000000000000'
          // cmask    '11000000000000000000000001100000'
          // cmaskinv '00111111111111111111111110011111'
          // asmask   '11000000000000000000000001100000'
          Seq(
            BitSet(BitPat("b01??????????????????????00??????"), BitPat("b10??????????????????????00??????")),
            BitSet(BitPat("b01??????????????????????01??????"), BitPat("b10??????????????????????01??????")),
            BitSet(BitPat("b01??????????????????????10??????"), BitPat("b10??????????????????????10??????")),
            BitSet(BitPat("b01??????????????????????11??????"), BitPat("b10??????????????????????11??????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"ddrBank$idx", bs, 8, false) } ++
          // sRam bank 3G+ 256K/bank, 8banks
          Seq(
            BitSet(BitPat("b11000000000????????????000??????")),
            BitSet(BitPat("b11000000000????????????001??????")),
            BitSet(BitPat("b11000000000????????????010??????")),
            BitSet(BitPat("b11000000000????????????011??????")),
            BitSet(BitPat("b11000000000????????????100??????")),
            BitSet(BitPat("b11000000000????????????101??????")),
            BitSet(BitPat("b11000000000????????????110??????")),
            BitSet(BitPat("b11000000000????????????111??????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"sramBank$idx", bs, 8, false) },
      vrfBankSize = 2,
      vrfRamType = RamType.p0rp1w,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        divfpModuleParameters = Seq(),
        otherModuleParameters =
          Seq((
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3))),
        floatModuleParameters = Seq(),
        zvbbModuleParameters = Seq() // TODO
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  // DLEN1K  VLEN4K ; NOFP; VRF p0rw       bank4; LSU bank16 beatbyte 16
  @main def sandslash(
    @arg(name = "target-file", short = 't') targetFile:             os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen = 4096
    val dLen = 1024
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32x"),
      t1customInstructions = Nil,
      lsuBankParameters =
        // scalar bank 0-1G
        Seq(
          BitSet(BitPat("b00??????????????????????????????"))
        ).map(bs => LSUBankParameter("scalar", bs, 8, true)) ++
          // ddr bank 1G-3G 512M/bank
          Seq(
            BitSet(BitPat("b01?????????????????????00???????"), BitPat("b10?????????????????????00???????")),
            BitSet(BitPat("b01?????????????????????01???????"), BitPat("b10?????????????????????01???????")),
            BitSet(BitPat("b01?????????????????????10???????"), BitPat("b10?????????????????????10???????")),
            BitSet(BitPat("b01?????????????????????11???????"), BitPat("b10?????????????????????11???????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"ddrBank$idx", bs, 8, false) } ++
          // sRam bank 3G+ 256K/bank, 16banks
          Seq(
            BitSet(BitPat("b1100000000?????????0000?????????")),
            BitSet(BitPat("b1100000000?????????0001?????????")),
            BitSet(BitPat("b1100000000?????????0010?????????")),
            BitSet(BitPat("b1100000000?????????0011?????????")),
            BitSet(BitPat("b1100000000?????????0100?????????")),
            BitSet(BitPat("b1100000000?????????0101?????????")),
            BitSet(BitPat("b1100000000?????????0110?????????")),
            BitSet(BitPat("b1100000000?????????0111?????????")),
            BitSet(BitPat("b1100000000?????????1000?????????")),
            BitSet(BitPat("b1100000000?????????1001?????????")),
            BitSet(BitPat("b1100000000?????????1010?????????")),
            BitSet(BitPat("b1100000000?????????1011?????????")),
            BitSet(BitPat("b1100000000?????????1100?????????")),
            BitSet(BitPat("b1100000000?????????1101?????????")),
            BitSet(BitPat("b1100000000?????????1110?????????")),
            BitSet(BitPat("b1100000000?????????1111?????????"))
          ).zipWithIndex.map { case (bs: BitSet, idx: Int) => LSUBankParameter(s"sramBank$idx", bs, 8, false) },
      vrfBankSize = 4,
      vrfRamType = RamType.p0rw,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        divfpModuleParameters = Seq(),
        otherModuleParameters =
          Seq((
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3))),
        floatModuleParameters = Seq(),
        zvbbModuleParameters = Seq() // TODO
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
