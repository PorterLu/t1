// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}

class TLBParameter() extends SerializableModuleParameter {
  val pmpCheckerParameter: PMPCheckerParameter = ???
}

class TLBInterface(parameter: TLBParameter) extends Bundle {

}

class TLB(val parameter: TLBParameter)
  extends FixedIORawModule(new TLBInterface(parameter))
    with SerializableModule[TLBParameter] {
  val pmpChecker: Instance[PMPChecker] = Instantiate(new PMPChecker(parameter.pmpCheckerParameter))
}