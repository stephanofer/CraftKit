package com.hera.craftkit.feedback.internal;

enum FeedbackOutputType {
    NONE,
    CHAT,
    CENTERED_CHAT,
    ACTION_BAR,
    TITLE,
    SOUND,
    BOSS_BAR;

    static FeedbackOutputType from(Object value) {
        if (value == null) {
            return NONE;
        }
        try {
            return valueOf(value.toString().trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
