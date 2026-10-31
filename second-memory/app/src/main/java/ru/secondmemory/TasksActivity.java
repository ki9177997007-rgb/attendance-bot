package ru.secondmemory;
import android.app.*;
import android.os.Bundle;
import android.widget.*;
import java.time.*;
import java.time.format.*;
public class TasksActivity extends Activity {
 private LinearLayout list;
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(24,24,24,24);scroll.addView(list);setContentView(scroll);ScreenLayout.apply(this,list);}
 @Override public void onResume(){super.onResume();refresh();}
 private void refresh(){list.removeAllViews();TextView title=new TextView(this);title.setText("Поручения · нажми для изменения");title.setTextSize(22);list.addView(title);
  Button add=new Button(this);add.setText("Добавить напоминание");add.setOnClickListener(v->startActivity(new android.content.Intent(this,CommandsActivity.class)));list.addView(add);
  try(TaskDb db=new TaskDb(this)){for(TaskDb.Row row:db.all()){Button b=new Button(this);String due=row.due==0?"срок не задан":Instant.ofEpochMilli(row.due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
   b.setText((row.completed?"✓ ГОТОВО":"○ ОТКРЫТО")+"\n"+row.text+"\n"+row.assignee+" · "+due);b.setOnClickListener(v->edit(row));list.addView(b);}}
 }
 private void edit(TaskDb.Row row){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,8,24,8);EditText text=new EditText(this);text.setText(row.text);text.setHint("Поручение");form.addView(text);
  AutoCompleteTextView staff=new AutoCompleteTextView(this);staff.setHint("Исполнитель");staff.setText(row.assignee);staff.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,Staff.get(this).split("[,;\n]+")));form.addView(staff);
  final long[] due={row.due};Button date=new Button(this);date.setText(row.due==0?"Назначить напоминание":new java.util.Date(row.due).toString());form.addView(date);
  date.setOnClickListener(v->{ZonedDateTime d=Instant.ofEpochMilli(due[0]>0?due[0]:System.currentTimeMillis()).atZone(ZoneId.systemDefault());new DatePickerDialog(this,(picker,y,m,day)->new TimePickerDialog(this,(time,h,min)->{due[0]=LocalDate.of(y,m+1,day).atTime(h,min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();date.setText(new java.util.Date(due[0]).toString());},d.getHour(),d.getMinute(),true).show(),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();});
  Button clear=new Button(this);clear.setText("Убрать напоминание");clear.setOnClickListener(v->{due[0]=0;date.setText("Назначить напоминание");});form.addView(clear);
  CheckBox done=new CheckBox(this);done.setText("Выполнено");done.setChecked(row.completed);form.addView(done);
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Изменить поручение").setView(scroll).setPositiveButton("Сохранить",null).setNeutralButton("Скопировать",(d,w)->{android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);cm.setPrimaryClip(android.content.ClipData.newPlainText("Поручение",text.getText().toString()));}).setNegativeButton("Назад",null).create();
  dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String t=text.getText().toString().trim();if(t.isEmpty()){text.setError("Введи поручение");return;}if(!done.isChecked()&&due[0]>0&&due[0]<System.currentTimeMillis()){Toast.makeText(this,"Выбери будущее время или убери напоминание",Toast.LENGTH_LONG).show();return;}
   try(TaskDb db=new TaskDb(this)){db.edit(row.id,t,staff.getText().toString().trim(),due[0],done.isChecked());}
   ReminderReceiver.schedule(this,row.id,done.isChecked()?0:due[0]);if(done.isChecked())getSystemService(NotificationManager.class).cancel(row.id,2);SyncEngine.kick(this);dialog.dismiss();refresh();}));dialog.show();
 }
}
