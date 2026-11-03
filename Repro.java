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
import jdk.incubator.vector.VectorSpecies;

import java.util.Random;

public class Repro {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    static void butterflyVector(float[] dataR, float[] dataI, float[] twR, float[] twI, int n, int half) {
        int step = 2 * half;
        int laneCount = SPECIES.length();
        int loopBound = SPECIES.loopBound(half);
        for (int even = 0; even < n; even += step) {
            int odd = even + half;
            int r = 0;
            for (; r < loopBound; r += laneCount) {
                var gR = FloatVector.fromArray(SPECIES, dataR, even + r);
                var gI = FloatVector.fromArray(SPECIES, dataI, even + r);
                var hR = FloatVector.fromArray(SPECIES, dataR, odd + r);
                var hI = FloatVector.fromArray(SPECIES, dataI, odd + r);
                var wR = FloatVector.fromArray(SPECIES, twR, r);
                var wI = FloatVector.fromArray(SPECIES, twI, r);
                var tR = wR.mul(hR).sub(wI.mul(hI));
                var tI = wR.mul(hI).add(wI.mul(hR));
                gR.add(tR).intoArray(dataR, even + r);
                gI.add(tI).intoArray(dataI, even + r);
                gR.sub(tR).intoArray(dataR, odd + r);
                gI.sub(tI).intoArray(dataI, odd + r);
            }
            for (; r < half; r++) {
                butterflyScalarAt(dataR, dataI, twR, twI, even + r, odd + r, r);
            }
        }
    }

    static void butterflyScalar(float[] dataR, float[] dataI, float[] twR, float[] twI, int n, int half) {
        for (int even = 0; even < n; even += 2 * half) {
            for (int r = 0; r < half; r++) {
                butterflyScalarAt(dataR, dataI, twR, twI, even + r, even + half + r, r);
            }
        }
    }

    private static void butterflyScalarAt(float[] dataR, float[] dataI, float[] twR, float[] twI, int e, int o, int r) {
        float gR = dataR[e];
        float gI = dataI[e];
        float hR = dataR[o];
        float hI = dataI[o];
        float tR = twR[r] * hR - twI[r] * hI;
        float tI = twR[r] * hI + twI[r] * hR;
        dataR[e] = gR + tR;
        dataI[e] = gI + tI;
        dataR[o] = gR - tR;
        dataI[o] = gI - tI;
    }

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 300_000;
        System.out.println(Runtime.version() + " " + System.getProperty("java.vm.name") + ", " + SPECIES);
        var random = new Random(42);
        int firstBad = -1;
        double maxError = 0;
        for (int i = 0; i < iterations; i++) {
            var re = new float[n];
            var im = new float[n];
            for (int k = 0; k < n; k++) {
                re[k] = random.nextFloat() - 0.5f;
                im[k] = random.nextFloat() - 0.5f;
            }
            var expR = re.clone();
            var expI = im.clone();
            for (int half = 4; half < n; half <<= 1) {
                var twR = new float[half];
                var twI = new float[half];
                for (int k = 0; k < half; k++) {
                    twR[k] = (float) Math.cos(-Math.PI * k / half);
                    twI[k] = (float) Math.sin(-Math.PI * k / half);
                }
                butterflyScalar(expR, expI, twR, twI, n, half);
                butterflyVector(re, im, twR, twI, n, half);
            }
            double error = 0;
            for (int k = 0; k < n; k++) {
                error = Math.max(error, Math.max(Math.abs(re[k] - expR[k]), Math.abs(im[k] - expI[k])));
            }
            if (error > 1e-4 && firstBad < 0) {
                firstBad = i;
            }
            maxError = Math.max(maxError, error);
        }
        boolean ok = firstBad < 0;
        System.out.printf("n=%d: %s (max error %.3g%s)%n", n, ok ? "PASS" : "FAIL", maxError,
                ok ? "" : ", first wrong iteration " + firstBad);
        System.exit(ok ? 0 : 1);
    }
}
