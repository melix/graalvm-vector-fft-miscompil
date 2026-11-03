# Vector API loop miscompiled by Oracle GraalVM 25.4/25.5 JIT

A radix-2 FFT butterfly loop written with `FloatVector` returns wrong results once the Graal JIT compiles it.

Affected: Oracle GraalVM 25.4.4.1.1 and 25.5.5.0.3.

Not affected: C2 (`-XX:-UseJVMCICompiler`), Oracle GraalVM 25.0.5, GraalVM CE 25, 25.4.4 and 25.5.5, native image.

The code comes from [astro4j](https://github.com/melix/astro4j), where it broke image alignment.

## Running

```sh
java --add-modules jdk.incubator.vector Repro.java
java --add-modules jdk.incubator.vector full-fft/FftRepro.java
```

Both exit with status 1 on failure. `run.sh` runs both with default flags, `-XX:UseAVX=2` and `-XX:-UseJVMCICompiler`:

```sh
JAVA_HOME=/path/to/graalvm ./run.sh
```

- `Repro.java` is the minimized loop, checked against the same loop written with scalars. It fails with AVX-512 only.
- `full-fft/FftRepro.java` runs the full FFT and checks it against a naive DFT. It fails with AVX-512 and AVX2.

The failing iteration varies from run to run, but every run failed on affected versions.

Tested on an AMD Ryzen 9 9950X (AVX-512) and on a GitHub Actions `windows-latest` runner (AVX2).

## Output on Oracle GraalVM 25.5.5.0.3

```
=== Repro.java (default)
25.0.5.0.3+1-LTS-jvmci-25.5-b26 Java HotSpot(TM) 64-Bit Server VM, Species[float, 16, S_512_BIT]
n=64: FAIL (max error 8.14, first wrong iteration 47520)

=== full-fft/FftRepro.java (default)
25.0.5.0.3+1-LTS-jvmci-25.5-b26 Java HotSpot(TM) 64-Bit Server VM, Species[float, 16, S_512_BIT]
n=16: PASS (max error vs. naive DFT 9.09e-07)
n=32: FAIL (max error vs. naive DFT 5.58, first wrong iteration 282000)
n=64: FAIL (max error vs. naive DFT 11.2, first wrong iteration 41000)

=== full-fft/FftRepro.java -XX:UseAVX=2
25.0.5.0.3+1-LTS-jvmci-25.5-b26 Java HotSpot(TM) 64-Bit Server VM, Species[float, 8, S_256_BIT]
n=16: PASS (max error vs. naive DFT 9.09e-07)
n=32: FAIL (max error vs. naive DFT 5.00, first wrong iteration 328000)
n=64: FAIL (max error vs. naive DFT 11.2, first wrong iteration 55000)
```

With `-XX:-UseJVMCICompiler`, everything passes (max error 6.92e-06 vs. the DFT).

## Isolating the method

Excluding the vectorized method from compilation makes it pass:

```sh
java -XX:CompileCommand=quiet \
     -XX:CompileCommand=exclude,Repro::butterflyVector \
     -XX:CompileCommand=dontinline,Repro::butterflyVector \
     --add-modules jdk.incubator.vector Repro.java
```

Excluding `butterflyScalar` instead doesn't. In the full FFT, excluding `VectorizedFloatFFT::radix2Stage` fixes it, while excluding every other method doesn't.

## Compiler options

Setting any of these to `false` makes both reproducers pass (3 runs out of 3 for `Repro.java`, plus `FftRepro.java` with `-XX:UseAVX=2`):

- `-Djdk.graal.Vectorization`
- `-Djdk.graal.OptimisticAliasingAnalysis`
- `-Djdk.graal.LoopPeeling`
- `-Djdk.graal.SimulationBasedLoopPeeling`
- `-Djdk.graal.SpeculativeGuardMovement`
- `-Djdk.graal.OptFloatingReads`

These don't help: `VectorIntrinsics`, `PartialUnroll`, `AggressivePartialUnroll`, `FullUnroll`, `LoopUnswitch`, `OptReadElimination`, `OptWriteSinking`, `PartialEscapeAnalysis`, `RangeCheckElimination`, `SpeculativeStoreCheck`.
