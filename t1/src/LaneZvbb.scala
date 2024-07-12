// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3.{UInt, _}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import hardfloat._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvbbParam {
  implicit def rw: upickle.default.ReadWriter[LaneZvbbParam] = upickle.default.macroRW
}

case class LaneZvbbParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvbbRequest(datapathWidth)
  val decodeField: BoolField = Decoder.zvbb
  val outputBundle = new LaneZvbbResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvbbRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src   = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
}

class LaneZvbbResponse(datapathWidth: Int)  extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

@instantiable
class LaneZvbb(val parameter: LaneZvbbParam) 
  extends VFUModule(parameter) with SerializableModule[LaneZvbbParam]{
  val response: LaneZvbbResponse = Wire(new LaneZvbbResponse(parameter.datapathWidth))
  val request : LaneZvbbRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val zvbbSrc: UInt = request.src(0) // vs2
  val zvbbRs: UInt = request.src(1) // vs1 or rs1

  val zvbbBRev  = VecInit(zvbbSrc(parameter.datapathWidth-1, 0).asBools.reverse).asUInt // element's bit reverse
  val zvbbBRev8 = VecInit(zvbbSrc(parameter.datapathWidth-1, 0).asBools.grouped(8).map(s => VecInit(s.reverse)).toSeq).asUInt // byte's bit reverse
  val zvbbRev8  = VecInit(zvbbSrc(parameter.datapathWidth-1, 0).asBools.grouped(8).map(s => VecInit(s)).toSeq.reverse).asUInt // element's byte reverse

  val zvbbCLZ = UInt(parameter.datapathWidth.W)
  for (i <- 0 until parameter.datapathWidth) {
    when(zvbbSrc(parameter.datapathWidth-i-1) === 1.U) {
      zvbbCLZ := zvbbCLZ + 1.U
    }
  }
  val zvbbCTZ = UInt(parameter.datapathWidth.W)
  for (i <- 0 until parameter.datapathWidth) {
    when(zvbbSrc(i) === 1.U) {
      zvbbCTZ := zvbbCTZ + 1.U
    }
  }
  val zvbbROL = zvbbSrc.rotateLeft(zvbbRs)
  val zvbbROR = zvbbSrc.rotateRight(zvbbRs)
  response.data := Mux1H(UIntToOH(request.opcode), Seq(
      zvbbBRev,
      zvbbBRev8,
      zvbbRev8,
      zvbbCLZ,
      zvbbCTZ,
      zvbbROL,
      zvbbROR,
    )
  )
}

