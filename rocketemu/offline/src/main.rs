mod difftest;
mod dut;
mod json_events;

use clap::Parser;
use tracing::info;

use common::spike_runner::SpikeRunner;
use common::CommonArgs;

use crate::difftest::Difftest;

fn run_spike(args: &mut CommonArgs) -> anyhow::Result<()> {
  let mut count: u64 = 0;

  let spike = SpikeRunner::new(args, true);
  loop {
    count += 1;
    if count % 1000000 == 0 {
      info!("count = {}", count);
    }
    match spike.exec() {
      Ok(_) => {}
      Err(_) => {
        info!("total v instrucions count = {}", count);
        info!("Simulation quit graceful");
        return Ok(());
      }
    };

    info!("exec inst over\n");
  }
}

fn main() -> anyhow::Result<()> {
  // parse args
  let mut args = CommonArgs::parse();

  args.setup_logger()?;

  // if there is no log file, just run spike and quit
  if args.log_file.is_none() {
    run_spike(&mut args)?;
    return Ok(());
  }

  // if there is a log file, run difftest
  let mut diff = Difftest::new(args);

  loop {
    match diff.diff() {
      Ok(_) => {}
      Err(e) => {
        info!("Simulation quit/error with {}", e);
        return Ok(());
      }
    }
  }
}
