package org.appxi.dictionary.app;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.appxi.dictionary.app.event.GenericEvent;
import org.appxi.dictionary.app.explorer.DictionaryContext;
import org.appxi.dictionary.app.explorer.HtmlBasedViewer;
import org.appxi.javafx.app.AppEvent;
import org.appxi.javafx.helper.FxHelper;
import org.appxi.javafx.settings.DefaultOptions;
import org.appxi.javafx.settings.SettingsList;
import org.appxi.prefs.UserPrefs;
import org.appxi.smartcn.convert.ChineseConvertors;
import org.appxi.util.ext.HanLang;

import java.util.Objects;

public abstract class AppContext {
    private AppContext() {
    }

    static void setupInitialize(App app) {
        DictionaryContext.setupInitialize(app, HtmlBasedViewer::getWebIncludeURIsEx, AppContext::hanText);
        //
        app.eventBus.addEventHandler(AppEvent.STARTED, e -> FxHelper.runThread(30, () -> DictionaryContext.openSearcherInEmbed(app, null)));
        //
        app.eventBus.addEventHandler(GenericEvent.HAN_LANG_CHANGED, event -> hanLang = event.data());
        //
        SettingsList.add(() -> {
            final ObjectProperty<HanLang> valueProperty = new SimpleObjectProperty<>(AppContext.hanLang());
            valueProperty.addListener((o, ov, nv) -> {
                if (null == ov || Objects.equals(ov, nv)) return;
                //
                UserPrefs.prefs.setProperty("display.han", nv.lang);
                app.eventBus.fireEvent(new GenericEvent(GenericEvent.HAN_LANG_CHANGED, nv));
            });
            return new DefaultOptions<HanLang>("简繁体", "以 简体/繁体 显示阅读视图中文字符", "VIEWER", true)
                    .setValues(HanLang.hans, HanLang.hant, HanLang.hantHK, HanLang.hantTW)
                    .setValueProperty(valueProperty);
        });
    }

    private static HanLang hanLang;

    public static HanLang hanLang() {
        if (null == hanLang)
            hanLang = HanLang.valueBy(UserPrefs.prefs.getString("display.han", HanLang.hans.lang));
        return hanLang;
    }

    public static String hanText(String text) {
        return null == text ? "" : ChineseConvertors.convert(text, HanLang.hantTW, hanLang());
    }
}
