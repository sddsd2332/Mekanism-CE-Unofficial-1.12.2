package mekanism.qioprocessing.common.terminal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIOPaginationTest {

    @Test
    void continuationIsBoundToTheSessionAndDirectoryRevision() {
        UUID sessionNonce = UUID.randomUUID();
        List<Integer> source = Arrays.asList(0, 1, 2, 3, 4);

        QIOPage<Integer> first = QIOPagination.page(source, 7, sessionNonce,
              null, 2, 4);

        assertEquals(Arrays.asList(0, 1), first.getEntries());
        assertEquals(0, first.getOffset());
        assertEquals(5, first.getTotalSize());
        QIOPageCursor continuation = first.getNextCursor();

        ByteBuf encoded = Unpooled.buffer();
        continuation.write(encoded);
        QIOPageCursor decoded = QIOPageCursor.read(encoded);
        QIOPage<Integer> second = QIOPagination.page(source, 7, sessionNonce,
              decoded, 2, 4);
        QIOPage<Integer> third = QIOPagination.page(source, 7, sessionNonce,
              second.getNextCursor(), 2, 4);

        assertEquals(Arrays.asList(2, 3), second.getEntries());
        assertEquals(Arrays.asList(4), third.getEntries());
        assertNull(third.getNextCursor());
        assertThrows(SecurityException.class, () -> QIOPagination.page(source, 7,
              UUID.randomUUID(), continuation, 2, 4));
        assertThrows(IllegalStateException.class, () -> QIOPagination.page(source, 8,
              sessionNonce, continuation, 2, 4));
    }

    @Test
    void pageAndWireBoundsRejectMalformedRequests() {
        UUID sessionNonce = UUID.randomUUID();
        List<Integer> source = Arrays.asList(1, 2, 3);

        assertThrows(IllegalArgumentException.class, () ->
              QIOPagination.page(source, 0, sessionNonce, null, 5, 4));
        assertThrows(IllegalArgumentException.class, () ->
              QIOPagination.page(source, 0, sessionNonce,
                    new QIOPageCursor(sessionNonce, 0, 4), 1, 4));
        assertThrows(IllegalArgumentException.class, () ->
              QIOPageCursor.read(Unpooled.wrappedBuffer(new byte[]{1, 2, 3})));

        ByteBuf futureVersion = Unpooled.buffer();
        futureVersion.writeByte(2);
        futureVersion.writeZero(28);
        assertThrows(IllegalArgumentException.class,
              () -> QIOPageCursor.read(futureVersion));
    }
}
