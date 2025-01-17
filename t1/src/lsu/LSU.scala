// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.util._
import org.chipsalliance.amba.axi4.bundle.{AXI4BundleParameter, AXI4RWIrrevocable}
import org.chipsalliance.t1.rtl._

/**
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param paWidth physical address width
  */
case class LSUParameter(
                         datapathWidth:        Int,
                         chainingSize:         Int,
                         vLen:                 Int,
                         laneNumber:           Int,
                         paWidth:              Int,
                         sourceWidth:          Int,
                         sizeWidth:            Int,
                         maskWidth:            Int,
                         lsuMSHRSize:          Int,
                         toVRFWriteQueueSize:  Int,
                         transferSize:         Int,
                         // TODO: refactor to per lane parameter.
                         vrfReadLatency:       Int,
                         axi4BundleParameter:  AXI4BundleParameter,
                         name: String
                       ) {
  val sewMin: Int = 8

  /** the maximum address offsets number can be accessed from lanes for one time. */
  val maxOffsetPerLaneAccess: Int = datapathWidth * laneNumber / sewMin

  /** see [[MSHRParam.maskGroupWidth]]. */
  val maskGroupWidth: Int = maxOffsetPerLaneAccess

  /** see [[VParameter.maskGroupSize]]. */
  val maskGroupSize: Int = vLen / datapathWidth

  /** hardware width of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** hardware width of [[CSRInterface.vl]]
    *
    * `+1` is for vl being 0 to vl(not vlMax - 1).
    * we use less than for comparing, rather than less equal.
    */
  val vLenBits: Int = log2Ceil(vLen) + 1

  val sourceQueueSize: Int = vLen * 8 / (transferSize * 8)

  def mshrParam: MSHRParam =
    MSHRParam(chainingSize, datapathWidth, vLen, laneNumber, paWidth, transferSize, vrfReadLatency)

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)
}

class LSUSlotProbe(param: LSUParameter) extends Bundle {
  val dataVd: UInt = UInt(param.regNumBits.W)
  val dataOffset: UInt = UInt(param.vrfOffsetBits.W)
  val dataMask: UInt = UInt((param.datapathWidth / 8).W)
  val dataData: UInt = UInt(param.datapathWidth.W)
  val dataInstruction: UInt = UInt(param.instructionIndexBits.W)
  val writeValid: Bool = Bool()
  val targetLane: UInt = UInt(log2Ceil(param.laneNumber).W)
}

class MemoryWriteProbe(param: MSHRParam) extends Bundle {
  val valid: Bool = Bool()
  val data: UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask: UInt = UInt(param.lsuTransposeSize.W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
  val address: UInt = UInt(param.paWidth.W)
}

class LSUProbe(param: LSUParameter) extends Bundle {
  // lsu write queue enq probe
  val slots = Vec(param.laneNumber, new LSUSlotProbe(param))
  val storeUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val otherUnitProbe = new MemoryWriteProbe(param.mshrParam)
  val reqEnq: UInt = UInt(param.lsuMSHRSize.W)
  val lsuInstructionValid: UInt = UInt((param.chainingSize * 2).W)
}

/** Load Store Unit
  * it is instantiated in [[V]],
  * it contains
  * - a bunch of [[MSHR]] to record outstanding memory transactions.
  * - a crossbar to connect memory interface and each lanes.
  */
@instantiable
class LSU(param: LSUParameter) extends Module {

  /** [[LSURequest]] from Scheduler to LSU
    * [[request.ready]] couples to [[request.bits]] to detect memory conflict.
    * There will be two cases that [[request.ready]] is false:
    *  - LSU slots is full.
    *  - memory conflict is detected.
    */
  @public
  val request: DecoupledIO[LSURequest] = IO(Flipped(Decoupled(new LSURequest(param.datapathWidth))))

  /** mask from [[V]]
    * TODO: since mask is one-cycle information for a mask group,
    *       we should latch it in the LSU, and reduce the IO width.
    *       this needs PnR information.
    */
  @public
  val maskInput: Vec[UInt] = IO(Input(Vec(param.lsuMSHRSize, UInt(param.maskGroupWidth.W))))

  /** the address of the mask group in the [[V]]. */
  @public
  val maskSelect: Vec[UInt] = IO(Output(Vec(param.lsuMSHRSize, UInt(param.maskGroupSizeBits.W))))

  @public
  val axi4Port: AXI4RWIrrevocable = IO(new AXI4RWIrrevocable(param.axi4BundleParameter))

  @public
  val simpleAccessPorts: AXI4RWIrrevocable = IO(new AXI4RWIrrevocable(param.axi4BundleParameter.copy(dataWidth=32)))

  /** read channel to [[V]], which will redirect it to [[Lane.vrf]].
    * [[vrfReadDataPorts.head.ready]] will be deasserted if there are VRF hazards.
    * [[vrfReadDataPorts.head.valid]] is from MSHR in LSU
    *
    * if fire, the next cycle [[vrfReadResults]] should be valid in the next cycle.
    */
  @public
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top.
    * TODO: merge to [[vrfReadDataPorts]]
    */
  @public
  val vrfReadResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]]. */
  @public
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(
        new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
      )
    )
  )

  @public
  val dataInWriteQueue: Vec[UInt] = IO(Output(Vec(param.laneNumber, UInt(param.chainingSize.W))))

  /** the CSR interface from [[V]], CSR will be latched in MSHR.
    * TODO: merge to [[LSURequest]]
    */
  @public
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vLenBits)))

  /** offset of indexed load/store instructions. */
  @public
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

  /** which instruction is requesting the offset.
    * TODO: merge to [[offsetReadResult]]
    */
  @public
  val offsetReadIndex: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.instructionIndexBits.W))))

  /** interface to [[V]], indicate a MSHR slots is finished, and corresponding instruction can commit. */
  @public
  val lastReport: UInt = IO(Output(UInt(param.chainingSize.W)))

  @public
  val lsuMaskGroupChange: UInt = IO(Output(UInt(param.chainingSize.W)))

  /** interface to [[V]], redirect to [[org.chipsalliance.t1.rtl.Lane]].
    * this group of offset is finish, request the next group of offset.
    */
  @public
  val lsuOffsetRequest: Bool = IO(Output(Bool()))
  @public
  val writeReadyForLsu: Bool = IO(Input(Bool()))
  @public
  val vrfReadyToStore: Bool = IO(Input(Bool()))

  // TODO: make it D/I
  val loadUnit: LoadUnit = Module(new LoadUnit(param.mshrParam))
  val storeUnit: StoreUnit = Module(new StoreUnit(param.mshrParam))
  val otherUnit: SimpleAccessUnit = Module(new SimpleAccessUnit(param.mshrParam))

  val unitVec = Seq(loadUnit, storeUnit, otherUnit)

  /** Always merge into cache line */
  val alwaysMerge: Bool = (
    request.bits.instructionInformation.mop ##
      // unit stride & whole register
      request.bits.instructionInformation.lumop(2, 0) ##
      request.bits.instructionInformation.lumop(4)
    ) === 0.U
  val useLoadUnit: Bool = alwaysMerge && !request.bits.instructionInformation.isStore
  val useStoreUnit: Bool = alwaysMerge && request.bits.instructionInformation.isStore
  val useOtherUnit: Bool = !alwaysMerge
  val addressCheck: Bool = otherUnit.status.idle && (!useOtherUnit || (loadUnit.status.idle && storeUnit.status.idle))
  val unitReady: Bool = (useLoadUnit && loadUnit.status.idle) || (useStoreUnit && storeUnit.status.idle) || (useOtherUnit && otherUnit.status.idle)
  request.ready := unitReady && addressCheck
  val requestFire = request.fire
  val reqEnq: Vec[Bool] = VecInit(Seq(useLoadUnit && requestFire, useStoreUnit && requestFire, useOtherUnit && requestFire))

  unitVec.zipWithIndex.foreach { case(mshr, index) =>
    mshr.lsuRequest.valid := reqEnq(index)
    mshr.lsuRequest.bits := request.bits

    maskSelect(index) := Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskInput := maskInput(index)

    // broadcast CSR
    mshr.csrInterface := csrInterface
  }

  /** TileLink D Channel write to VRF queue:
   * TL-D -CrossBar-> MSHR -proxy-> write queue -CrossBar-> VRF
   */
  @public
  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] = Seq.fill(param.laneNumber)(
    Module(new Queue(new LSUWriteQueueBundle(param), param.toVRFWriteQueueSize, flow = true))
  )

  @public
  val _probe = IO(Output(Probe(new LSUProbe(param))))
  val probeWire = Wire(new LSUProbe(param))
  define(_probe, ProbeValue(probeWire))

  // read vrf
  val otherTryReadVrf: UInt = Mux(otherUnit.vrfReadDataPorts.valid, otherUnit.status.targetLane, 0.U)
  vrfReadDataPorts.zipWithIndex.foreach { case (read, index) =>
    read.valid := otherTryReadVrf(index) || storeUnit.vrfReadDataPorts(index).valid
    read.bits := Mux(otherTryReadVrf(index), otherUnit.vrfReadDataPorts.bits, storeUnit.vrfReadDataPorts(index).bits)
    storeUnit.vrfReadDataPorts(index).ready := read.ready && !otherTryReadVrf(index)
    storeUnit.vrfReadResults(index) := vrfReadResults(index)
  }
  otherUnit.vrfReadDataPorts.ready := (otherTryReadVrf & VecInit(vrfReadDataPorts.map(_.ready)).asUInt).orR
  val pipeOtherRead: ValidIO[UInt] =
    Pipe(otherUnit.vrfReadDataPorts.fire, otherUnit.status.targetLane, param.vrfReadLatency)
  otherUnit.vrfReadResults.bits := Mux1H(pipeOtherRead.bits, vrfReadResults)
  otherUnit.vrfReadResults.valid := pipeOtherRead.valid

  // write vrf
  val otherTryToWrite: UInt = Mux(otherUnit.vrfWritePort.valid, otherUnit.status.targetLane, 0.U)
  // Permission to enter the queue TODO: Investigate why this happens
  val canEnterQueue: Vec[Bool] = Wire(Vec(param.laneNumber, Bool()))
  // other 优先级更高
  otherUnit.vrfWritePort.ready := (otherUnit.status.targetLane & VecInit(writeQueueVec.map(_.io.enq.ready)).asUInt).orR
  writeQueueVec.zipWithIndex.foreach {case (write, index) =>
    write.io.enq.valid := otherTryToWrite(index) || loadUnit.vrfWritePort(index).valid
    write.io.enq.bits.data := Mux(otherTryToWrite(index), otherUnit.vrfWritePort.bits, loadUnit.vrfWritePort(index).bits)
    write.io.enq.bits.targetLane := (BigInt(1) << index).U
    loadUnit.vrfWritePort(index).ready := write.io.enq.ready && !otherTryToWrite(index)

    // probes
    probeWire.slots(index).dataVd := write.io.enq.bits.data.vd
    probeWire.slots(index).dataOffset := write.io.enq.bits.data.offset
    probeWire.slots(index).dataMask := write.io.enq.bits.data.mask
    probeWire.slots(index).dataData := write.io.enq.bits.data.data
    probeWire.slots(index).dataInstruction := write.io.enq.bits.data.instructionIndex
    probeWire.slots(index).writeValid := write.io.enq.valid
    probeWire.slots(index).targetLane := OHToUInt(write.io.enq.bits.targetLane)
  }
  probeWire.reqEnq := reqEnq.asUInt

  probeWire.storeUnitProbe := probe.read(storeUnit.probe)
  probeWire.otherUnitProbe := probe.read(otherUnit.probe)
  probeWire.lsuInstructionValid :=
    // The load unit becomes idle when it writes vrf for the last time.
    maskAnd(!loadUnit.status.idle || VecInit(loadUnit.vrfWritePort.map(_.valid)).asUInt.orR,
      indexToOH(loadUnit.status.instructionIndex, 2 * param.chainingSize)).asUInt |
      maskAnd(!storeUnit.status.idle, indexToOH(storeUnit.status.instructionIndex, 2 * param.chainingSize)).asUInt |
      maskAnd(!otherUnit.status.idle, indexToOH(otherUnit.status.instructionIndex, 2 * param.chainingSize)).asUInt

  vrfWritePort.zip(writeQueueVec).foreach { case (p, q) =>
    p.valid := q.io.deq.valid
    p.bits := q.io.deq.bits.data
    q.io.deq.ready := p.ready
  }

  val dataInMSHR: UInt =
    Mux(loadUnit.status.idle, 0.U(param.chainingSize.W), indexToOH(loadUnit.status.instructionIndex, param.chainingSize)) |
      Mux(
        otherUnit.status.idle || otherUnit.status.isStore,
        0.U(param.chainingSize.W),
        indexToOH(otherUnit.status.instructionIndex, param.chainingSize)
      )

  // Record whether there is data for the corresponding instruction in the queue
  writeQueueVec.zip(dataInWriteQueue).zipWithIndex.foreach {case ((q, p), queueIndex) =>
    val queueCount: Seq[UInt] = Seq.tabulate(param.chainingSize) { _ =>
      RegInit(0.U(log2Ceil(param.toVRFWriteQueueSize).W))
    }
    val enqOH: UInt = indexToOH(q.io.enq.bits.data.instructionIndex, param.chainingSize)
    val queueEnq: UInt = Mux(q.io.enq.fire, enqOH, 0.U)
    val queueDeq = Mux(q.io.deq.fire, indexToOH(q.io.deq.bits.data.instructionIndex, param.chainingSize), 0.U)
    queueCount.zipWithIndex.foreach {case (count, index) =>
      val counterUpdate: UInt = Mux(queueEnq(index), 1.U, -1.S(log2Ceil(param.toVRFWriteQueueSize).W).asUInt)
      when(queueEnq(index) ^ queueDeq(index)) {
        count := count + counterUpdate
      }
    }
    p := VecInit(queueCount.map(_ =/= 0.U)).asUInt | dataInMSHR
    val dataTag = VecInit(Seq.tabulate(param.chainingSize) { _ =>
      RegInit(false.B)
    })
    val nextTag = q.io.enq.bits.data.instructionIndex.asBools.last
    val currentTag = (dataTag.asUInt & enqOH).orR
    // same tage or nothing in queue
    canEnterQueue(queueIndex) := (nextTag === currentTag) || !p
    dataTag.zipWithIndex.foreach {case (d, i) =>
      when(q.io.deq.fire && enqOH(i)) {
        d := nextTag
      }
    }
  }

  val sourceQueue = Module(new Queue(UInt(param.mshrParam.sourceWidth.W), param.sourceQueueSize))
  // load unit connect
  axi4Port.ar.valid := loadUnit.memRequest.valid && sourceQueue.io.enq.ready
  axi4Port.ar.bits <> DontCare
  axi4Port.ar.bits.addr := loadUnit.memRequest.bits.address
  axi4Port.ar.bits.len := 0.U
  axi4Port.ar.bits.size := param.mshrParam.cacheLineBits.U
  axi4Port.ar.bits.burst := 1.U //INCR
  loadUnit.memRequest.ready := sourceQueue.io.enq.ready && axi4Port.ar.ready

  loadUnit.memResponse.valid := axi4Port.r.valid
  loadUnit.memResponse.bits.data := axi4Port.r.bits.data
  loadUnit.memResponse.bits.index := sourceQueue.io.deq.bits
  axi4Port.r.ready := loadUnit.memResponse.ready

  sourceQueue.io.enq.valid := loadUnit.memRequest.valid && axi4Port.ar.ready
  sourceQueue.io.enq.bits := loadUnit.memRequest.bits.src
  sourceQueue.io.deq.ready := axi4Port.r.fire

  // store unit <> axi
  val dataQueue: Queue[MemWrite] = Module(new Queue(chiselTypeOf(storeUnit.memRequest.bits), 2))
  axi4Port.aw.valid := storeUnit.memRequest.valid && dataQueue.io.enq.ready
  axi4Port.aw.bits <> DontCare
  axi4Port.aw.bits.len := 0.U
  axi4Port.aw.bits.burst := 1.U //INCR
  axi4Port.aw.bits.size := param.mshrParam.cacheLineBits.U
  axi4Port.aw.bits.addr := storeUnit.memRequest.bits.address
  axi4Port.aw.bits.id := storeUnit.memRequest.bits.index
  storeUnit.memRequest.ready := axi4Port.aw.ready && dataQueue.io.enq.ready

  dataQueue.io.enq.valid := storeUnit.memRequest.valid && axi4Port.aw.ready
  dataQueue.io.enq.bits := storeUnit.memRequest.bits

  axi4Port.w.valid := dataQueue.io.deq.valid
  axi4Port.w.bits <> DontCare
  axi4Port.w.bits.data := dataQueue.io.deq.bits.data
  axi4Port.w.bits.strb := dataQueue.io.deq.bits.mask
  axi4Port.w.bits.last := true.B
  dataQueue.io.deq.ready := axi4Port.w.ready

  // todo: add write token ?
  axi4Port.b.ready := true.B
  storeUnit.storeResponse := axi4Port.b.valid
  simpleAccessPorts.b.ready := true.B

  // other unit <> axi
  val simpleSourceQueue: Queue[UInt] = Module(new Queue(UInt(param.mshrParam.sourceWidth.W), param.sourceQueueSize))
  simpleAccessPorts.ar.valid := otherUnit.memReadRequest.valid && simpleSourceQueue.io.enq.ready
  simpleAccessPorts.ar.bits <> DontCare
  simpleAccessPorts.ar.bits.addr := otherUnit.memReadRequest.bits.address
  simpleAccessPorts.ar.bits.len := 0.U
  simpleAccessPorts.ar.bits.size := otherUnit.memReadRequest.bits.size
  simpleAccessPorts.ar.bits.burst := 1.U //INCR
  otherUnit.memReadRequest.ready := simpleSourceQueue.io.enq.ready && simpleAccessPorts.ar.ready

  otherUnit.memReadResponse.valid := simpleAccessPorts.r.valid
  otherUnit.memReadResponse.bits.data := simpleAccessPorts.r.bits.data
  otherUnit.memReadResponse.bits.source := simpleSourceQueue.io.deq.bits
  simpleAccessPorts.r.ready := otherUnit.memReadResponse.ready

  simpleSourceQueue.io.enq.valid := otherUnit.memReadRequest.valid && simpleAccessPorts.ar.ready
  simpleSourceQueue.io.enq.bits := otherUnit.memReadRequest.bits.source
  simpleSourceQueue.io.deq.ready := simpleAccessPorts.r.fire

  val simpleDataQueue: Queue[SimpleMemWrite] = Module(new Queue(chiselTypeOf(otherUnit.memWriteRequest.bits), 2))
  simpleAccessPorts.aw.valid := otherUnit.memWriteRequest.valid && dataQueue.io.enq.ready
  simpleAccessPorts.aw.bits <> DontCare
  simpleAccessPorts.aw.bits.len := 0.U
  simpleAccessPorts.aw.bits.burst := 1.U //INCR
  simpleAccessPorts.aw.bits.size := otherUnit.memWriteRequest.bits.size
  simpleAccessPorts.aw.bits.addr := otherUnit.memWriteRequest.bits.address
  simpleAccessPorts.aw.bits.id := otherUnit.memWriteRequest.bits.source
  otherUnit.memWriteRequest.ready := simpleAccessPorts.aw.ready && simpleDataQueue.io.enq.ready

  simpleDataQueue.io.enq.valid := otherUnit.memWriteRequest.valid && simpleAccessPorts.aw.ready
  simpleDataQueue.io.enq.bits := otherUnit.memWriteRequest.bits

  simpleAccessPorts.w.valid := simpleDataQueue.io.deq.valid
  simpleAccessPorts.w.bits <> DontCare
  simpleAccessPorts.w.bits.data := simpleDataQueue.io.deq.bits.data
  simpleAccessPorts.w.bits.strb := simpleDataQueue.io.deq.bits.mask
  simpleAccessPorts.w.bits.last := true.B
  simpleDataQueue.io.deq.ready := simpleAccessPorts.w.ready

  otherUnit.offsetReadResult := offsetReadResult

  // gather last signal from all MSHR to notify LSU
  lastReport :=
    unitVec.map(m => Mux(m.status.last, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)).reduce(_ | _)
  lsuMaskGroupChange := unitVec.map(
    m => Mux(m.status.changeMaskGroup, indexToOH(m.status.instructionIndex, param.chainingSize), 0.U)
  ).reduce(_ | _)
  lsuOffsetRequest := (otherUnit.status.offsetGroupEnd | otherUnit.status.last |
    (otherUnit.status.idle && offsetReadResult.map(_.valid).reduce(_ | _))) && otherUnit.status.isIndexLS
  loadUnit.writeReadyForLsu := writeReadyForLsu
  storeUnit.vrfReadyToStore := vrfReadyToStore

  val unitOrder: Bool = instIndexLE(loadUnit.status.instructionIndex, storeUnit.status.instructionIndex)
  val loadAddressConflict: Bool = (loadUnit.status.startAddress >= storeUnit.status.startAddress) &&
    (loadUnit.status.startAddress <= storeUnit.status.endAddress)
  val storeAddressConflict: Bool = (storeUnit.status.startAddress >= loadUnit.status.startAddress) &&
    (storeUnit.status.startAddress <= loadUnit.status.endAddress)

  val stallLoad: Bool = !unitOrder && loadAddressConflict && !storeUnit.status.idle
  val stallStore: Bool = unitOrder && storeAddressConflict && !loadUnit.status.idle

  loadUnit.addressConflict := stallLoad
  storeUnit.addressConflict := stallStore
}
