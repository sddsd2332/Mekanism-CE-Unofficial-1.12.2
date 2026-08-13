package mekanism.qioprocessing.common.util;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOHashingTest {

    @Test
    void sha256RemainsPersistedFormatCompatible() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              QIOHashing.sha256(""));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
              QIOHashing.sha256("abc"));
    }

    @Test
    void threadLocalDigestsAreSafeAcrossPlanningWorkers() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(IntStream.range(0, 2_000).parallel().allMatch(
              ignored -> expected.equals(QIOHashing.sha256("abc"))));
    }
}
