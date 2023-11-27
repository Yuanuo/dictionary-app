package org.appxi.dictionary.app;

import org.appxi.dictionary.ui.DictionaryContext;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.app.DesktopApp;
import org.appxi.javafx.app.web.WebViewer;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.settings.SettingsList;
import org.appxi.smartcn.convert.ChineseConvertors;
import org.appxi.util.ext.HanLang;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class AppContext {
    private AppContext() {
    }

    static void setupInitialize(App app) {
        DictionaryContext.setupInitialize(app, () -> {
            List<String> result = WebViewer.getWebIncludeURIs();
            final Path dir = DesktopApp.appDir().resolve("template/web-incl");
            result.addAll(Stream.of("html-viewer.css", "html-viewer.js")
                    .map(s -> dir.resolve(s).toUri().toString())
                    .toList()
            );
            result.add("<link id=\"CSS\" rel=\"stylesheet\" type=\"text/css\" href=\"" + app.visualProvider.getWebStyleSheetURI() + "\">");
            return result;
        }, HanLang::convert);
        //
        app.eventBus.addEventHandler(AppEvent.STARTED, e -> FxHelper.runThread(30, () -> DictionaryContext.openSearcherInEmbed(app, null)));
        //
        HanLang.setup(app.eventBus, (text, lang) -> ChineseConvertors.convert(text, null, lang));
        //
        SettingsList.add(() -> FxHelper.optionForHanLang("以 简体/繁体 显示阅读视图中文字符"));
    }
}
