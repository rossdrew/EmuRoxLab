package com.rox;

/**
 * XXX Make non static
 */
public class Clock {
    private static final double FPS = 60; //PAL
    private static final long FRAME_TIME_NS = (long) (1_000_000_000 / FPS);
    private static Ticker ticker;
    private boolean running = true;

    public void run(){
        while (running) {
            //XXX This could be wrapped in a executeWithinFrame(()->{})
            long frameStartTime = System.nanoTime();
            ticker.tick();
            long elapsedSinceFrameStart = System.nanoTime() - frameStartTime;
            long timeRemainingInFrame = FRAME_TIME_NS - elapsedSinceFrameStart;

            //Wait till next frame
            if (timeRemainingInFrame > 0){
                sleepFor(timeRemainingInFrame);
            }

        }
    }

    private void sleepFor(long nanos) {
        long millis = nanos / 1_000_000;
        int remainingNanos = (int) (nanos % 1_000_000);

        try {
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            /*LOGGER*/System.out.println("Error while trying to sleep.");
        }
    }


}
