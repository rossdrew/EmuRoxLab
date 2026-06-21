package com.rox;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;

import static net.jqwik.api.Arbitraries.*;

public class Arbitraries {
    @Provide
    Arbitrary<Integer> byteValue() {
        return integers()
                .between(0, 0xFF);
    }

    @Provide
    Arbitrary<Integer> nonByteValue() {
        return integers()
                .filter(i -> i < 0 || i > 255);
    }

    @Provide
    final Arbitrary<Integer> powersOfTwo() {
        return integers()
                .between(1, 16)
                .map(power -> 1 << power);
    }

    @Provide
    Arbitrary<Integer> nonPowersOfTwo() {
        return integers()
                .between(1, 100_000)
                .filter(n -> (n & (n - 1)) != 0);
    }
}
