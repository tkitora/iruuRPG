package net.tkgon.mc.iruuRPG.quest;

public enum QuestState {
    AVAILABLE,
    IN_PROGRESS,
    /** Goal reached: the reward can be collected at the board. */
    READY,
    DONE
}
