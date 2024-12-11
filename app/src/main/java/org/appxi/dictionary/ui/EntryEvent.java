package org.appxi.dictionary.ui;

import org.appxi.event.Event;
import org.appxi.event.EventType;

public class EntryEvent extends Event {
    public static final EventType<EntryEvent> SEARCH = new EventType<>(Event.ANY);
    public static final EventType<EntryEvent> SEARCH_EXACT = new EventType<>(Event.ANY);

    public final String text, dictionary;

    public EntryEvent(EventType<EntryEvent> eventType, String text, String dictionary) {
        super(eventType);
        this.text = text;
        this.dictionary = dictionary;
    }
}
