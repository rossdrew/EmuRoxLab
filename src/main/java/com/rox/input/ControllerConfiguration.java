package com.rox.input;

/**
 * Bundles all 4 controller slots plus the Four Score enablement flag into the one parameter threaded
 * through {@link com.rox.NES}/{@link com.rox.mem.NESMemoryBus} - matches how
 * {@link com.rox.video.VideoOutput} was added as one bundled param per pluggable-device layer, not
 * one param per slot.
 */
public class ControllerConfiguration {
    /** All 4 slots absent, Four Score disabled - the default for callers that don't care about input. */
    public static final ControllerConfiguration NONE =
            new ControllerConfiguration(Controller.NONE, Controller.NONE, Controller.NONE, Controller.NONE, false);

    private final Controller player1;
    private final Controller player2;
    private final Controller player3;
    private final Controller player4;
    private final boolean fourScoreEnabled;

    public ControllerConfiguration(final Controller player1, final Controller player2,
                                    final Controller player3, final Controller player4,
                                    final boolean fourScoreEnabled){
        this.player1 = player1;
        this.player2 = player2;
        this.player3 = player3;
        this.player4 = player4;
        this.fourScoreEnabled = fourScoreEnabled;
    }

    /** 2-player setup, Four Score disabled - the common case. */
    public static ControllerConfiguration twoPlayers(final Controller player1, final Controller player2){
        return new ControllerConfiguration(player1, player2, Controller.NONE, Controller.NONE, false);
    }

    public Controller player1(){
        return player1;
    }

    public Controller player2(){
        return player2;
    }

    public Controller player3(){
        return player3;
    }

    public Controller player4(){
        return player4;
    }

    public boolean fourScoreEnabled(){
        return fourScoreEnabled;
    }
}
