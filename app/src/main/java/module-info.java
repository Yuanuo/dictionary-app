module appxi.smartDictionary {
    requires transitive java.desktop;
    requires transitive appxi.javafx;
    requires transitive appxi.dictionary.api;

    exports org.appxi.dictionary.app;
    exports org.appxi.dictionary.app.explorer;

    opens org.appxi.dictionary.app;
}