package org.appxi.dictionary.app.event;

import org.appxi.event.Event;
import org.appxi.event.EventType;

public class GenericEvent extends Event {
    public static final EventType<GenericEvent> HAN_LANG_CHANGED = new EventType<>(Event.ANY, "HAN_LANG_CHANGED");

    public GenericEvent(EventType<GenericEvent> eventType) {
        this(eventType, null);
    }

    public GenericEvent(EventType<GenericEvent> eventType, Object data) {
        super(eventType, data);
    }
}
