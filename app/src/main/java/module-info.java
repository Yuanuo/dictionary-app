module appxi.dictionary {
    requires transitive java.desktop;
    requires transitive appxi.javafx;
    requires transitive appxi.dictionary.api;

    exports org.appxi.dictionary.app;
    exports org.appxi.dictionary.ui;

    opens org.appxi.dictionary.app;
}