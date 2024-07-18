// This file includes DPI call implementatitons

#include "svdpi.h"

#include "dpi.h"

extern "C" {

void *dpi_call_target;

/// evaluate after AW and W is finished at corresponding channel_id.
void axi_write_highBandwidthPort(long long channel_id, long long awid,
                                 long long awaddr, long long awlen,
                                 long long awsize, long long awburst,
                                 long long awlock, long long awcache,
                                 long long awprot, long long awqos,
                                 long long awregion,
                                 /// struct packed {bit [255:0][DLEN:0] data;
                                 /// bit [255:0][DLEN/8:0] strb; } payload
                                 const svBitVecVal *payload) {
  axi_write_highBandwidthPort_rs(dpi_call_target, channel_id, awid, awaddr,
                                 awlen, awsize, awburst, awlock, awcache,
                                 awprot, awqos, awregion, payload);
};

/// evaluate at AR fire at corresponding channel_id.
void axi_read_highBandwidthPort(
    long long channel_id, long long arid, long long araddr, long long arlen,
    long long arsize, long long arburst, long long arlock, long long arcache,
    long long arprot, long long arqos, long long arregion,
    /// struct packed {bit [255:0][DLEN:0] data; byte beats; } payload
    svBitVecVal *payload) {
  axi_read_highBandwidthPort_rs(dpi_call_target, channel_id, arid, araddr,
                                arlen, arsize, arburst, arlock, arcache, arprot,
                                arqos, arregion, payload);
};

/// evaluate at AR fire at corresponding channel_id.
void axi_read_indexedAccessPort(
    long long channel_id, long long arid, long long araddr, long long arlen,
    long long arsize, long long arburst, long long arlock, long long arcache,
    long long arprot, long long arqos, long long arregion,
    /// struct packed {bit [255:0][31:0] data; byte beats; } payload
    svBitVecVal *payload) {
  axi_read_indexedAccessPort_rs(dpi_call_target, channel_id, arid, araddr,
                                arlen, arsize, arburst, arlock, arcache, arprot,
                                arqos, arregion, payload);
};

/// evaluate after AW and W is finished at corresponding channel_id.
void axi_write_indexedAccessPort(
    long long channel_id, long long awid, long long awaddr, long long awlen,
    long long awsize, long long awburst, long long awlock, long long awcache,
    long long awprot, long long awqos, long long awregion,
    /// struct packed {bit [255:0][32:0] data; bit [255:0][4:0] strb; } payload
    const svBitVecVal *payload) {
  axi_write_indexedAccessPort_rs(dpi_call_target, channel_id, awid, awaddr,
                                 awlen, awsize, awburst, awlock, awcache,
                                 awprot, awqos, awregion, payload);
}

/// evaluate after reset, and will only be called once returning *call_init =
/// true.
void cosim_init() { dpi_call_target = cosim_init_rs(); }

/// evaluate at every 1024 cycles, return reason = 0 to continue simulation,
/// other value is used as error code.
void cosim_watchdog(char *reason) {
  cosim_watchdog_rs(dpi_call_target, reason);
}

/// evaluate at instruction queue is not empty
/// arg issue will be type cast from a struct to svBitVecVal*(uint32_t*)
void issue_vector_instruction(
    /// struct issue_data {
    ///   uint32_t instruction;
    ///   uint32_t src1_data;
    ///   uint32_t src2_data;
    ///   uint32_t vtype;
    ///   uint32_t vl;
    ///   uint32_t vstart;
    ///   uint32_t vcsr;
    /// }
    svBitVecVal *issue) {
  issue_vector_instruction_rs(dpi_call_target, issue);
}

void retire_vector_instruction(const svBitVecVal *retire) {
  retire_vector_instruction_rs(dpi_call_target, retire);
}

void retire_vector_mem(const svBitVecVal *retire) {
  retire_vector_mem_rs(dpi_call_target);
}

} // extern "C"