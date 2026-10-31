package ru.secondmemory;
import android.content.Context;
final class Staff {
 static String get(Context c){return c.getSharedPreferences("settings",0).getString("staff","");}
 static void set(Context c,String names){c.getSharedPreferences("settings",0).edit().putString("staff",(names.length()>1500?names.substring(0,1500):names).trim()).apply();}
}
