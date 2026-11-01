package ru.secondmemory;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
/** Insets are applied once to the outer container; scroll content remains reachable. */
final class ScreenLayout {
 static void apply(Activity a,LinearLayout page){int space=Ui.dp(a,20);page.setBackgroundColor(Ui.BG);a.getWindow().setStatusBarColor(Ui.BG);a.getWindow().setNavigationBarColor(Ui.BG);a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);page.setPadding(space,space,space,space);page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(space+i.getSystemWindowInsetLeft(),space+i.getSystemWindowInsetTop(),space+i.getSystemWindowInsetRight(),space+i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});page.requestApplyInsets();if(!(a instanceof MainActivity))page.post(()->Ui.polish(page));}
}
