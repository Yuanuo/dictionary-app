package org.appxi.dictionary.app.explorer;

import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.javafx.workbench.WorkbenchPartController;
import org.appxi.util.StringHelper;

class DictionaryEmbedSearcher extends WorkbenchPartController.MainView {
    public DictionaryEmbedSearcher(String viewId, WorkbenchPane workbench) {
        super(workbench);

        this.id.set(viewId);
        this.setTitles(null);
    }

    protected void setTitles(String appendText) {
        String title = "查词";
        if (null != appendText)
            title = title + "：" + (appendText.isBlank() ? "*" : StringHelper.trimChars(appendText, 16));
        this.title.set(title);
        this.tooltip.set(title);
        this.appTitle.set(title);
    }

    @Override
    public void postConstruct() {
    }

    private DictionaryLookupLayer lookupPane;

    @Override
    protected void createViewport(StackPane viewport) {
        super.createViewport(viewport);
        //
        lookupPane = new DictionaryLookupLayer(app);
        lookupPane.setPadding(new Insets(10));
        lookupPane.setSpacing(10);
        viewport.getChildren().addAll(lookupPane);

        lookupPane.textInput.input.textProperty().addListener((o, ov, nv) -> setTitles(nv));
    }

    @Override
    public void activeViewport(boolean firstTime) {
        FxHelper.runThread(50, lookupPane.textInput.input::requestFocus);
    }

    @Override
    public void inactiveViewport(boolean closing) {
    }

    boolean isNeverSearched() {
        return null != lookupPane && lookupPane.getSearchedCount() < 1;
    }

    void search(String text) {
        //
//        setTitles(inputQuery.length() > 20 ? inputQuery.substring(0, 20) : inputQuery);
//        final List<String> selectedScopes = scopeListView.getItems().stream()
//                .filter(v -> v.stateProperty.get()).map(v -> v.id).toList();
//        lookupPane.dictionaryPredicate = dictionary -> selectedScopes.isEmpty() || selectedScopes.contains(dictionary.id);
        //
        lookupPane.search(text);
    }
}
