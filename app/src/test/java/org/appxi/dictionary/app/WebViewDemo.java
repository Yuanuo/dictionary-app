package org.appxi.dictionary.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;

public class WebViewDemo extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        VBox vBox = new VBox();
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        webEngine.setUserDataDirectory(new File(System.getProperty("java.io.tmpdir")));

//        File htmlFle = new File("C:\\smartLib.dd\\01. ཀུན་དགའ་ཡེ་ཤེས་རྒྱ་མཚོའི་གསུང་འབུམ། ༼ཀ༽\\01. ཐེག་མཆོག་ཤིན་ཏུ་རྒྱས་པའི་དབུ་མ་ཆེན་པོ་རྣམ་པར་ངེས་པའི་རྣམ་བཤད་ཟིན་བྲིས་བཞུགས།.html");
        File htmlFle = File.createTempFile("testJfx", ".html");
        htmlFle.deleteOnExit();
        Files.writeString(htmlFle.toPath(), """
         <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: "Microsoft YaHei";
                            font-size: 2rem;
                        }
                    </style>
                </head>
                <body>
                    <p>【韵会】𠀤莫六切，音目。</p>
                    <p>【说文】本作𤘴，养牛人也。从攴牛。</p>
                </body>
                </html>
                """);
        System.out.println(htmlFle.toURI());
        webEngine.load(htmlFle.toURI().toString());

        vBox.getChildren().add(webView);
        Scene scene = new Scene(vBox, 800, 600);
        primaryStage.setTitle("JavaFX Version " + System.getProperty("javafx.runtime.version"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
 