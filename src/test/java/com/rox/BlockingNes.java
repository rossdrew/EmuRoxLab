package com.rox;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Mock {@link NES}es whose {@code powerOn} blocks, like a real one, until {@code powerOff}. */
final class BlockingNes {
    private BlockingNes(){
    }

    static NES create(){
        return create(new AtomicReference<>());
    }

    /** @param poweredOnBy set to the thread that calls {@code powerOn} */
    static NES create(final AtomicReference<Thread> poweredOnBy){
        final NES nes = mock(NES.class);
        final CountDownLatch poweredOff = new CountDownLatch(1);
        doAnswer(invocation -> {
            poweredOnBy.set(Thread.currentThread());
            poweredOff.await();
            return null;
        }).when(nes).powerOn();
        doAnswer(invocation -> {
            poweredOff.countDown();
            return null;
        }).when(nes).powerOff();
        return nes;
    }
}
