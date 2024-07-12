{ lib
, fetchFromGitHub
, linkerScript
, makeBuilder
, t1main
, isFp
}:

let
  src = fetchFromGitHub {
    owner = "camel-cdr";
    repo = "rvv-bench";
    rev = "5dc20c3596b3aa8412804e2d169d1b175bae927a";
    hash = "sha256-5A079sl4g7FIWgCYykLgTZXrmyfIblyXtxeh1AwqKiU=";
    fetchSubmodules = true;
  };

  nonFpCases = [
    "ascii_to_utf16"
    "ascii_to_utf32"
    "byteswap"
    "chacha20"
    "memcpy"
    "memset"
    "mergelines"
    "poly1305"
    "strlen"
    "utf8_count"
  ];

  fpCases = [
    "mandelbrot"
  ];

  cases = nonFpCases ++ (lib.optionals isFp fpCases);

  builder = makeBuilder { casePrefix = "rvv_bench"; };
  build = caseName:
    let
      drv = builder
        {
          inherit caseName src;

          isFp = lib.elem caseName fpCases;

          patches = [ ./t1_runtime.patch ];

          buildPhase = ''
            runHook preBuild
            pushd bench >/dev/null

            $CC -E -DINC=$PWD/${caseName}.S template.S -E -o functions.S
            $CC ${caseName}.c -T${linkerScript} ${t1main} functions.S -o ../$pname.elf

            popd >/dev/null
            runHook postBuild
          '';

          meta.description = "test case '${caseName}' from rvv-bench";
        };
    in
    drv;
in
lib.genAttrs cases build

