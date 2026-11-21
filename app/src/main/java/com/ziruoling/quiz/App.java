package com.ziruoling.quiz;

import android.app.Application;

import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.util.Tr;

/** 应用入口：初始化本地数据库与设置 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Db.init(this);
        Tr.init(this);
    }
}