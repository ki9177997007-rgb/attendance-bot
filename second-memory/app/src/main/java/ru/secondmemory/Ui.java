package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native design: warm canvas, ink, teal, generous touch targets. */
final class Ui {
 static final int BG=Color.rgb(245,247,246),INK=Color.rgb(20,40,43),MUTED=Color.rgb(91,111,111),TEAL=Color.rgb(0,109,100),SOFT=Color.rgb(227,242,235),LINE=Color.rgb(220,230,225),RED=Color.rgb(165,49,54);
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int radius,boolean border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));if(border)g.setStroke(dp(c,1),LINE);return g;}
 static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTag(Boolean.TRUE);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(true);t.setLineSpacing(dp(c,2),1);t.setTypeface(Typeface.create(size>=20?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return t;}
 static TextView label(Context c,String value,int size){TextView t=text(c,value,size,size>=19?INK:MUTED);t.setTextIsSelectable(true);t.setPadding(0,dp(c,6),0,dp(c,8));return t;}
 static LinearLayout column(Context c){LinearLayout p=new LinearLayout(c);p.setOrientation(LinearLayout.VERTICAL);return p;}
 static LinearLayout card(LinearLayout parent){Context c=parent.getContext();LinearLayout p=column(c);p.setPadding(dp(c,18),dp(c,14),dp(c,18),dp(c,14));p.setBackground(shape(c,Color.WHITE,22,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,12);parent.addView(p,lp);return p;}
 static void buttonStyle(Button b,boolean primary){b.setTag(Boolean.TRUE);Context c=b.getContext();int color=primary?TEAL:Color.WHITE;b.setAllCaps(false);b.setTextSize(15);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?Color.WHITE:INK}));b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(c,54));b.setMinimumHeight(dp(c,54));b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22006d64),shape(c,color,14,!primary),null));}
 static Button button(LinearLayout parent,String label,boolean primary,Runnable action){Button b=new Button(parent.getContext());b.setText(label);buttonStyle(b,primary);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(b.getContext(),6);lp.bottomMargin=dp(b.getContext(),4);parent.addView(b,lp);return b;}
 static void field(EditText e){e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(16);e.setPadding(dp(e.getContext(),14),dp(e.getContext(),14),dp(e.getContext(),14),dp(e.getContext(),14));e.setBackground(shape(e.getContext(),Color.WHITE,14,true));e.setMinHeight(dp(e.getContext(),56));if(e.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)e.getLayoutParams();lp.topMargin=dp(e.getContext(),4);lp.bottomMargin=dp(e.getContext(),8);e.setLayoutParams(lp);}}
 static void polish(View view){if(Boolean.TRUE.equals(view.getTag())){}else if(view instanceof EditText)field((EditText)view);else if(view instanceof Button&&!(view instanceof android.widget.CompoundButton))buttonStyle((Button)view,false);else if(view instanceof TextView){TextView t=(TextView)view;t.setTextColor(INK);if(t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=20)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)polish(g.getChildAt(i));}}
 static void update(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
 static void copy(Context c,String title,String value){c.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(title,value));Toast.makeText(c,"Скопировано",Toast.LENGTH_SHORT).show();}
 static void title(LinearLayout parent,String title,String subtitle){parent.addView(text(parent.getContext(),title,28,INK));if(!subtitle.isEmpty()){TextView sub=label(parent.getContext(),subtitle,14);sub.setPadding(0,dp(parent.getContext(),6),0,dp(parent.getContext(),18));parent.addView(sub);}}
 static void section(LinearLayout parent,String title){TextView t=text(parent.getContext(),title,18,INK);t.setPadding(dp(parent.getContext(),2),dp(parent.getContext(),16),0,dp(parent.getContext(),10));parent.addView(t);}
 static String bytes(long bytes){double gb=bytes/1073741824.0;return gb>=1?String.format(new java.util.Locale("ru"),"%.1f ГБ",gb):String.format(new java.util.Locale("ru"),"%.0f МБ",bytes/1048576.0);}
}
