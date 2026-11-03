/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import jdk.incubator.vector.FloatVector;

import java.util.Random;

public class FftRepro {
    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 500_000;
        System.out.println(Runtime.version() + " " + System.getProperty("java.vm.name") + ", " + FloatVector.SPECIES_PREFERRED);
        var random = new Random(42);
        boolean failed = false;
        for (int n : new int[]{16, 32, 64}) {
            int firstBad = -1;
            double maxError = 0;
            for (int i = 0; i < iterations; i++) {
                var re = new float[n];
                var im = new float[n];
                for (int k = 0; k < n; k++) {
                    re[k] = random.nextFloat() - 0.5f;
                    im[k] = random.nextFloat() - 0.5f;
                }
                var fft = new VectorizedFloatFFT(re.clone(), im.clone());
                fft.transform();
                if (i % 1000 == 0) {
                    double error = 0;
                    for (int k = 0; k < n; k++) {
                        double sr = 0;
                        double si = 0;
                        for (int t = 0; t < n; t++) {
                            double a = -2 * Math.PI * k * t / n;
                            sr += re[t] * Math.cos(a) - im[t] * Math.sin(a);
                            si += re[t] * Math.sin(a) + im[t] * Math.cos(a);
                        }
                        error = Math.max(error, Math.hypot(sr - fft.real()[k], si - fft.imaginary()[k]));
                    }
                    if (error > 1e-3 && firstBad < 0) {
                        firstBad = i;
                    }
                    maxError = Math.max(maxError, error);
                }
            }
            boolean ok = firstBad < 0;
            failed |= !ok;
            System.out.printf("n=%d: %s (max error vs. naive DFT %.3g%s)%n", n, ok ? "PASS" : "FAIL", maxError,
                    ok ? "" : ", first wrong iteration " + firstBad);
        }
        System.out.println(failed ? "FAIL" : "PASS");
        System.exit(failed ? 1 : 0);
    }
}
