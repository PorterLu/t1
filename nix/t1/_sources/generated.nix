# This file was generated by nvfetcher, please do not modify it manually.
{ fetchgit, fetchurl, fetchFromGitHub, dockerTools }:
{
  arithmetic = {
    pname = "arithmetic";
    version = "4a81e23e1794844b36c53385d343475d4d7eca49";
    src = fetchFromGitHub {
      owner = "sequencer";
      repo = "arithmetic";
      rev = "4a81e23e1794844b36c53385d343475d4d7eca49";
      fetchSubmodules = false;
      sha256 = "sha256-tQwzECNOXhuKzpwRD+iKSEJYl1/wlhMQTJULJSCdTrY=";
    };
    date = "2024-01-23";
  };
  berkeley-hardfloat = {
    pname = "berkeley-hardfloat";
    version = "b3c8a38c286101973b3bc071f7918392343faba7";
    src = fetchFromGitHub {
      owner = "ucb-bar";
      repo = "berkeley-hardfloat";
      rev = "b3c8a38c286101973b3bc071f7918392343faba7";
      fetchSubmodules = false;
      sha256 = "sha256-3j6K/qFuH8PqJT6zHVTIphq9HWxmSGoIqDo9GV1bxmU=";
    };
    date = "2023-10-25";
  };
  cde = {
    pname = "cde";
    version = "52768c97a27b254c0cc0ac9401feb55b29e18c28";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "cde";
      rev = "52768c97a27b254c0cc0ac9401feb55b29e18c28";
      fetchSubmodules = false;
      sha256 = "sha256-bmiVhuriiuDFFP5gXcP2kKwdrFQ2I0Cfz3N2zed+IyY=";
    };
    date = "2023-08-05";
  };
  chisel = {
    pname = "chisel";
    version = "344bdd636a36e6c3933cac527c61b5671d271534";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "chisel";
      rev = "344bdd636a36e6c3933cac527c61b5671d271534";
      fetchSubmodules = false;
      sha256 = "sha256-SZNs0AeAQtenGatRbUftxAZOLI2G7KgEbAeW0ciAl0c=";
    };
    date = "2024-02-26";
  };
  diplomacy = {
    pname = "diplomacy";
    version = "edf375300d99a4c260a214d7c1553de0040771d7";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "diplomacy";
      rev = "edf375300d99a4c260a214d7c1553de0040771d7";
      fetchSubmodules = false;
      sha256 = "sha256-3WuzrzFaQnVsSEpQTBjO+Xy1z+ouH0TjKI1AS02/bhQ=";
    };
    date = "2024-03-11";
  };
  riscv-opcodes = {
    pname = "riscv-opcodes";
    version = "61d2ef45dcb4a276a1e69643880cb75a9ca5ba79";
    src = fetchFromGitHub {
      owner = "riscv";
      repo = "riscv-opcodes";
      rev = "61d2ef45dcb4a276a1e69643880cb75a9ca5ba79";
      fetchSubmodules = false;
      sha256 = "sha256-jdXKNIigKAqn2bbrMn6HxB61AM8KwSCvFEoL1N604rw=";
    };
    date = "2023-11-27";
  };
  rocket-chip = {
    pname = "rocket-chip";
    version = "4fa4a8df81f0f058e50bfdcb36e32ade912eadb5";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "rocket-chip";
      rev = "4fa4a8df81f0f058e50bfdcb36e32ade912eadb5";
      fetchSubmodules = false;
      sha256 = "sha256-Dq3tSx4neQWs5aqEnR8Hc/kHVU9z2sBAcHUF35w8bPc=";
    };
    date = "2024-03-11";
  };
  rocket-chip-inclusive-cache = {
    pname = "rocket-chip-inclusive-cache";
    version = "7f391c5e4cba3cdd4388efb778bd80da35d5574a";
    src = fetchFromGitHub {
      owner = "chipsalliance";
      repo = "rocket-chip-inclusive-cache";
      rev = "7f391c5e4cba3cdd4388efb778bd80da35d5574a";
      fetchSubmodules = false;
      sha256 = "sha256-mr3PA/wlXkC/Cu/H5T6l1xtBrK9KQQmGOfL3TMxq5T4=";
    };
    date = "2023-08-15";
  };
  rvdecoderdb = {
    pname = "rvdecoderdb";
    version = "d65525e7e18004b0877d8fbe2c435296ab986f44";
    src = fetchFromGitHub {
      owner = "sequencer";
      repo = "rvdecoderdb";
      rev = "d65525e7e18004b0877d8fbe2c435296ab986f44";
      fetchSubmodules = false;
      sha256 = "sha256-MzEoFjyUgarR62ux4ngYNFOgvAoeasdr1EVhaCvuh+Q=";
    };
    date = "2024-01-28";
  };
  tilelink = {
    pname = "tilelink";
    version = "cd177e4636eb4a20326795a66e9ab502f9b2500a";
    src = fetchFromGitHub {
      owner = "sequencer";
      repo = "tilelink";
      rev = "cd177e4636eb4a20326795a66e9ab502f9b2500a";
      fetchSubmodules = false;
      sha256 = "sha256-PIPLdZSCNKHBbho0YWGODSEM8toRBlOYC2gcbh+gqIY=";
    };
    date = "2023-08-11";
  };
}
