package org.appxi.dictionary.app;

import javafx.application.Preloader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.appxi.javafx.helper.FontFaceHelper;
import org.appxi.util.OSVersions;

import java.util.Optional;

public class AppPreloader extends Preloader {
    private static Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        AppPreloader.primaryStage = primaryStage;

        final ImageView imageView = new ImageView();
        Optional.ofNullable(AppPreloader.class.getResourceAsStream("splash.jpg"))
                .ifPresent(v -> imageView.setImage(new Image(v)));

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(new Scene(new BorderPane(imageView), 800, 498));

        primaryStage.setTitle(App.NAME);
        Optional.ofNullable(getClass().getResourceAsStream("icon-32.png"))
                .ifPresent(v -> primaryStage.getIcons().setAll(new Image(v)));
        primaryStage.centerOnScreen();
        primaryStage.show();
        //
        FontFaceHelper.fixFontFamilies();
        //
        if (OSVersions.isLinux || OSVersions.isMac) {
            new javafx.scene.control.TextField("");
            new javax.swing.JTextField("");
        }
    }

    public static void hide() {
        if (null != primaryStage) primaryStage.close();
    }
}
