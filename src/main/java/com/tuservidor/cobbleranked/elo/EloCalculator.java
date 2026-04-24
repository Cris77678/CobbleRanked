package com.tuservidor.cobbleranked.elo;

/**
 * Standard ELO rating system implementation.
 */
public class EloCalculator {

    /** K-factor: how much ELO can change per game */
    private static int kFactor = 32;

    public static void setKFactor(int k) { kFactor = k; }
    public static int getKFactor()       { return kFactor; }

    /**
     * Calculate new ELO ratings after a match.
     * @param winnerElo  ELO of the winner
     * @param loserElo   ELO of the loser
     * @return int[2] — { newWinnerElo, newLoserElo }
     */
    public static int[] calculate(int winnerElo, int loserElo) {
        double expectedWinner = expected(winnerElo, loserElo);
        double expectedLoser  = expected(loserElo, winnerElo);

        int newWinner = (int) Math.round(winnerElo + kFactor * (1.0 - expectedWinner));
        int newLoser  = (int) Math.round(loserElo  + kFactor * (0.0 - expectedLoser));

        // Floor at 0
        newWinner = Math.max(0, newWinner);
        newLoser  = Math.max(0, newLoser);

        return new int[]{ newWinner, newLoser };
    }

    /**
     * Calculate ELO change for a draw.
     * @return int[2] — { newEloA, newEloB }
     */
    public static int[] calculateDraw(int eloA, int eloB) {
        double expectedA = expected(eloA, eloB);
        double expectedB = expected(eloB, eloA);

        int newA = (int) Math.round(eloA + kFactor * (0.5 - expectedA));
        int newB = (int) Math.round(eloB + kFactor * (0.5 - expectedB));

        newA = Math.max(0, newA);
        newB = Math.max(0, newB);

        return new int[]{ newA, newB };
    }

    private static double expected(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }
}
