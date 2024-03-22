package org.appxi.dictionary.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.javafx.workbench.WorkbenchPart;
import org.appxi.javafx.workbench.WorkbenchPartController;

public class DictionaryController extends WorkbenchPartController implements WorkbenchPart.SideTool {
    public DictionaryController(WorkbenchPane workbench) {
        super(workbench);

        this.id.set("SEARCH-DICTIONARY-LAYER");
        this.title.set("查词条");
        this.tooltip.set("查词条 (Ctrl+D)");
        this.graphic.set(MaterialIcon.TRANSLATE.graphic());
    }

    @Override
    public boolean sideToolAlignTop() {
        return true;
    }

    @Override
    public void postConstruct() {
        app.getPrimaryScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN),
                () -> this.activeViewport(false));
    }

    @Override
    public void activeViewport(boolean firstTime) {
        app.eventBus.fireEvent(new EntryEvent(EntryEvent.SEARCH, null, null));
    }
}
