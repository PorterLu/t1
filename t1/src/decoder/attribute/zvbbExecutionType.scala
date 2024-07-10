// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object ZvbbExecutionType {
  trait Type extends Uop {
    def apply(t1DecodePattern: T1DecodePattern): Boolean
  }
  case object Brev extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = {
      val allMatched = if(t1DecodePattern.param.zvbbEnable) Seq(
        "vbrev.v"
      )
      allMatched.contains(t1DecodePattern.instruction.name)
    }
  }
  case object Brev8 extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = {
      val allMatched = if(t1DecodePattern.param.zvbbEnable) Seq(
        "vbrev8.v"
      )
      allMatched.contains(t1DecodePattern.instruction.name)
    }
  }
  case object Rev8 extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = {
      val allMatched = if(t1DecodePattern.param.zvbbEnable) Seq(
        "vrev8.v"
      )
      allMatched.contains(t1DecodePattern.instruction.name)
    }
  }
  case object NIL extends Type {
    def apply(t1DecodePattern: T1DecodePattern): Boolean = {
      require(requirement = false, "unreachable")
      false
    }
  }
  def apply(t1DecodePattern: T1DecodePattern): Type = {
    val tpe = Seq(Brev, Brev8, Rev8).filter(tpe =>
      tpe(t1DecodePattern)
    )
    require(tpe.size <= 1)
    tpe.headOption.getOrElse(Nil)
  }
}

case class ZvbbExecutionType(value: ZvbbExecutionType.Type) extends UopDecodeAttribute[ZvbbExecutionType.Type] {
  override val description: String = "zvbb uop"
}
