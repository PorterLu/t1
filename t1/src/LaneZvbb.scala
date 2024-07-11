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
  val outputBundle = new LaneZvbbResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvbbRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src   = Vec(3, UInt(datapathWidth.W)) // TODO: what is the order of vs1, vs2, vd
  val opcode = UInt(3.W)
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
  val zvbbBRev = UInt(parameter.datapathWidth.W) // element's bit reverse
  for (i <- 0 until parameter.datapathWidth) {
    zvbbBRev:= zvbbBRev ## zvbbSrc(i)
  }
  val zvbbBRev8 = UInt(parameter.datapathWidth.W) // byte's bit reverse
  for (i <- 0 until parameter.datapathWidth/8) {
    for (j <- 0 until 8) {
      zvbbBRev8 := zvbbBRev8 ## zvbbSrc(i * 8 + j)
    }
  }
  val zvbbRev8 = UInt(parameter.datapathWidth.W) // element's byte reverse
  for (i <- 0 until parameter.datapathWidth/8) {
    zvbbRev8:= zvbbRev8 ## zvbbSrc(parameter.datapathWidth - i * 8 - 1, parameter.datapathWidth - i * 8 - 1 - 8)
  }
  response.data := Mux(request.opcode(0), zvbbBRev,
    Mux(request.opcode(1), zvbbBRev8,
      Mux(reques.opcode(2), zvbbRev8, 
        zvbbSrc
      )
    )
  )
}

