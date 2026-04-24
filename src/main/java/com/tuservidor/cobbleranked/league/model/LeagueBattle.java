package com.tuservidor.cobbleranked.league.model;

import lombok.Data;

import java.util.UUID;

/**
 * Records a single league battle (challenger vs leader/E4/Champion).
 */
@Data
public class LeagueBattle {

    public enum Result { WIN, LOSS }

    private UUID   challengerUuid;
    private String challengerName;

    private UUID   memberUuid;
    private String memberName;
    private LeagueMember.Role memberRole;
    private int    memberSlot;

    /** Result FROM THE CHALLENGER's perspective */
    private Result result;

    private long timestamp;

    public LeagueBattle() {}

    public LeagueBattle(UUID challengerUuid, String challengerName,
                        LeagueMember member, Result result) {
        this.challengerUuid = challengerUuid;
        this.challengerName = challengerName;
        this.memberUuid     = member.getUuid();
        this.memberName     = member.getName();
        this.memberRole     = member.getRole();
        this.memberSlot     = member.getSlot();
        this.result         = result;
        this.timestamp      = System.currentTimeMillis();
    }

    public boolean challengerWon() { return result == Result.WIN; }
}
