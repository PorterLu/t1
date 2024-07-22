package org.chipsalliance.t1.omreaderlib

import scala.reflect.runtime.universe._
import chisel3.panamalib.option._
import chisel3.panamaom._
import chisel3.panamaconverter.PanamaCIRCTConverter

object OMReader {
  def fromFile(mlirbcFile: os.Path): OMReader = {
    new OMReader(os.read.bytes(mlirbcFile))
  }

  def fromBytes(mlirbc: Array[Byte]): OMReader = {
    new OMReader(mlirbc)
  }
}

class OMReader private(mlirbc: Array[Byte]) {
  private val cvt = PanamaCIRCTConverter.newWithMlirBc(mlirbc)
  private val om = cvt.om()
  private val evaluator = om.evaluator()

  def t1Reader: T1Reader = new T1Reader(evaluator, om.newBasePathEmpty)
}

class T1Reader private[omreaderlib](evaluator: PanamaCIRCTOMEvaluator, basePath: PanamaCIRCTOMEvaluatorValueBasePath) {
  val (entry, isSubsystem) = {
    evaluator.instantiate("T1Subsystem_Class", Seq(basePath)) match {
      case Some(subsystem) => (subsystem, true)
      case None => (evaluator.instantiate("T1_Class", Seq(basePath)).get, false)
    }
  }
  private val t1 = {
    if (isSubsystem) {
      entry
        .field("om").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
        .field("t1").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    }
    else {
      entry
        .field("om").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    }
  }

  def vlen: Long = t1.field("vlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer
  def dlen: Long = t1.field("dlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer
  private def decoderInstructionsJsonImpl: ujson.Value = {
    val decoder = t1.field("decoder").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    val instructions = decoder.field("instructions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

    instructions.elements.map(instruction => {
      val instr = instruction.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
      val attributes = instr.field("attributes").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

      ujson.Obj(
        "attributes" -> attributes.elements.map(attribute => {
          val attr = attribute.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
          val description = attr.field("description").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
          val identifier = attr.field("identifier").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
          val value = attr.field("value").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
          ujson.Obj(
            "description" -> description,
            "identifier" -> identifier,
            "value" -> value
          )
        })
      )
    })
  }
  def decoderInstructionsJson: String = ujson.write(decoderInstructionsJsonImpl)
  def decoderInstructionsJsonPretty: String = ujson.write(decoderInstructionsJsonImpl, 2)
  private def lanesJsonImpl: ujson.Value = {
    val lanes = t1.field("lanes").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    lanes.elements.map(lane => {
      val laneObj = lane.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
      val vfus = laneObj.field("vfus").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

      val vrf = laneObj.field("vrf").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
      val vrfSram = vrf.field("vrfSram").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

      ujson.Obj(
        "vfus" -> vfus.elements.map(vfu => {
          val vfuObj = vfu.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
          val cycles = vfuObj.field("cycles").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer
          ujson.Obj("cycles" -> ujson.Num(cycles))
        }),
        "vrf" -> ujson.Obj(
          "vrfSram" -> vrfSram.elements.map(sram => sram.asInstanceOf[PanamaCIRCTOMEvaluatorValuePath].toString),
        )
      )
    })
  }
  def lanesJson: String = ujson.write(lanesJsonImpl)
  def lanesJsonPretty: String = ujson.write(lanesJsonImpl, 2)

  def dumpHierarchy(): Unit = {
    def dumpHierarchyImpl(value: PanamaCIRCTOMEvaluatorValue, indent: Int): Unit = {
      value match {
        case obj: PanamaCIRCTOMEvaluatorValueObject =>
          obj.foreachField((name, next) => {
            println(s"${"  " * indent}$name")
            dumpHierarchyImpl(next, indent + 1)
          })
        case list: PanamaCIRCTOMEvaluatorValueList =>
          if (list.numElements > 0) {
            println(s"${"  " * indent}[")
            dumpHierarchyImpl(list.getElement(0), indent + 1)
            println(s"${"  " * indent}]")
          } else {
            // TODO: FIXME, there should be a way to get the element type without accessing the first element
            println(s"${"  " * indent}[???]")
          }
        case _ => {}
      }
    }
    dumpHierarchyImpl(entry, 0)
  }

  def dumpMethods(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader).reflect(this)
    val methods = typeOf[T1Reader].decls.toList.filter(
        m => m.isPublic && m.isMethod && !m.isConstructor && !m.asMethod.isGetter
    )
    methods.foreach(method => {
      if (!method.name.toString.startsWith("dump")) {
        var value = mirror.reflectMethod(method.asMethod)().toString.replace("\n", "\\n")

        val displayLength = 80
        if (value.length > displayLength) {
          value = value.take(displayLength) + s" (... ${value.length - displayLength} characters)"
        }

        println(s"${method.name} = $value")
      }
    })
  }

  def dumpAll(): Unit = {
    entry.foreachField((name, value) => println(s".$name => $value"))
  }
}
