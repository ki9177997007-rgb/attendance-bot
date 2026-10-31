package ru.secondmemory;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
/** Density-safe padding and system bars on edge-to-edge Android screens. */
final class ScreenLayout {
 static void apply(Activity a,LinearLayout page){int space=Math.round(16*a.getResources().getDisplayMetrics().density);page.setBackgroundColor(Color.rgb(244,247,252));page.setFitsSystemWindows(true);a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);page.setPadding(space,space,space,space);page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(space+i.getSystemWindowInsetLeft(),space+i.getSystemWindowInsetTop(),space+i.getSystemWindowInsetRight(),space+i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});page.requestApplyInsets();}
}
