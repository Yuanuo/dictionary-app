package org.appxi.dictionary.pref;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.settings.SettingsPane;
import org.appxi.javafx.visual.MaterialIcon;
import org.appxi.javafx.workbench.WorkbenchPane;
import org.appxi.javafx.workbench.WorkbenchPart;
import org.appxi.javafx.workbench.WorkbenchPartController;
import org.appxi.util.OSVersions;

public class PreferencesController extends WorkbenchPartController implements WorkbenchPart.SideTool {
    public PreferencesController(WorkbenchPane workbench) {
        super(workbench);

        this.id.set("PREFERENCES");
        this.title.set("设置");
        this.tooltip.set("设置");
        this.graphic.set(MaterialIcon.TUNE.graphic());
    }

    @Override
    public void postConstruct() {
        app.settings.add(() -> app.visualProvider().optionForFontSmooth());
        app.settings.add(() -> app.visualProvider().optionForFontName());
        app.settings.add(() -> app.visualProvider().optionForFontSize());
        app.settings.add(() -> app.visualProvider().optionForTheme());
        app.settings.add(() -> app.visualProvider().optionForSwatch());
        app.settings.add(() -> app.visualProvider().optionForWebFontName());
        app.settings.add(() -> app.visualProvider().optionForWebFontSize());
        app.settings.add(() -> app.visualProvider().optionForWebPageColor());
        app.settings.add(() -> app.visualProvider().optionForWebTextColor());
        //
    }

    @Override
    public void activeViewport(boolean firstTime) {
        SettingsPane settingsPane = new SettingsPane();

        app.settings.forEach(s -> settingsPane.getOptions().add(s.get()));

        final DialogPane dialogPane = new DialogPane() {
            @Override
            protected Node createButtonBar() {
                return null;
            }
        };
        if (OSVersions.isLinux) {
            dialogPane.setPrefSize(540, 720);
        }
        dialogPane.setContent(settingsPane);
        dialogPane.getButtonTypes().add(ButtonType.OK);
        //
        Dialog<?> dialog = new Dialog<>();
        dialog.setTitle(title.get());
        dialog.setDialogPane(dialogPane);
        dialog.getDialogPane().setPrefWidth(600);
        dialog.setResizable(true);
        dialog.initOwner(app.getPrimaryStage());
        dialog.setOnShown(evt -> FxHelper.runThread(100, () -> {
            dialog.setHeight(800);
            dialog.setY(dialog.getOwner().getY() + (dialog.getOwner().getHeight() - dialog.getHeight()) / 2);
            if (dialog.getX() < 0) dialog.setX(0);
            if (dialog.getY() < 0) dialog.setY(0);
        }));
        dialog.show();
    }
}
