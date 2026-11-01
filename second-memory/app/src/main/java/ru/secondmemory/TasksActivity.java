package ru.secondmemory;
import android.app.*;
import android.os.Bundle;
import android.widget.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
public class TasksActivity extends Activity {
 private LinearLayout list;private int filter;private String report="";
 @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)filter=b.getInt("filter",0);ScrollView scroll=new ScrollView(this);list=Ui.column(this);scroll.addView(list);setContentView(scroll);ScreenLayout.apply(this,list);}
 @Override public void onResume(){super.onResume();refresh();}
 protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("filter",filter);}
 private void refresh(){list.removeAllViews();Ui.title(list,"Мои дела","Задачи из разговоров и твои напоминания");
  LinearLayout actions=Ui.card(list);Ui.button(actions,"Добавить напоминание",true,()->{TaskDb.Row row=new TaskDb.Row();row.text="";row.assignee="";edit(row);});Ui.button(actions,"Скопировать всё",false,()->Ui.copy(this,"Поручения",report));
  HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(this);String[] names={"Открытые","Выполненные","Все"};for(int i=0;i<names.length;i++){final int index=i;Button b=new Button(this);b.setText(names[i]);Ui.buttonStyle(b,filter==i);b.setOnClickListener(v->{filter=index;refresh();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,Ui.dp(this,8),Ui.dp(this,12));choices.addView(b,lp);}scroll.addView(choices);list.addView(scroll);
  StringBuilder export=new StringBuilder(names[filter]+" поручения\n");int count=0;try(TaskDb db=new TaskDb(this)){List<TaskDb.Row> rows=db.all();rows.sort((a,b)->{if(a.completed!=b.completed)return a.completed?1:-1;long ad=a.due==0?Long.MAX_VALUE:a.due,bd=b.due==0?Long.MAX_VALUE:b.due;return Long.compare(ad,bd);});for(TaskDb.Row row:rows){if(filter==0&&row.completed||filter==1&&!row.completed)continue;count++;boolean overdue=!row.completed&&row.due>0&&row.due<System.currentTimeMillis();String due=row.due==0?"Без срока":AssistantEngine.date(row.due);LinearLayout card=Ui.card(list);card.addView(Ui.text(this,row.completed?"ВЫПОЛНЕНО":overdue?"СРОК ПРОШЁЛ":"В РАБОТЕ",11,overdue?Ui.RED:Ui.TEAL));card.addView(Ui.label(this,row.text,19));card.addView(Ui.label(this,(row.assignee.isEmpty()?"":row.assignee+" · ")+due,14));
   Ui.button(card,"Изменить",false,()->edit(row));Ui.button(card,row.completed?"Вернуть в дела":"Выполнить",false,()->{try(TaskDb change=new TaskDb(this)){change.edit(row.id,row.text,row.assignee,row.due,!row.completed);}ReminderReceiver.schedule(this,row.id,row.completed?row.due:0);getSystemService(NotificationManager.class).cancel(row.id,2);SyncEngine.kick(this);refresh();});
   export.append(row.completed?"✓ ":"○ ").append(row.text).append("\n").append(row.assignee.isEmpty()?"":row.assignee+" · ").append(due).append("\n\n");}}
  if(count==0){LinearLayout empty=Ui.card(list);empty.addView(Ui.label(this,filter==1?"Выполненные дела появятся здесь.":"Пока пусто. Добавь задачу или скажи: «Помощник, запиши задачу…».",17));}report=export.toString();Ui.button(list,"Назад",false,this::finish);
 }
 private void edit(TaskDb.Row row){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));EditText text=new EditText(this);text.setText(row.text);text.setHint("Поручение");form.addView(text);
  AutoCompleteTextView staff=new AutoCompleteTextView(this);staff.setHint("Исполнитель");staff.setText(row.assignee);staff.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,Staff.get(this).split("[,;\n]+")));form.addView(staff);
  final long[] due={row.due};Button date=new Button(this);date.setText(row.due==0?"Без срока · назначить напоминание":AssistantEngine.date(row.due));form.addView(date);
  date.setOnClickListener(v->{ZonedDateTime d=Instant.ofEpochMilli(due[0]>0?due[0]:System.currentTimeMillis()).atZone(ZoneId.systemDefault());new DatePickerDialog(this,(picker,y,m,day)->new TimePickerDialog(this,(time,h,min)->{due[0]=LocalDate.of(y,m+1,day).atTime(h,min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();date.setText(AssistantEngine.date(due[0]));},d.getHour(),d.getMinute(),true).show(),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();});
  Button clear=new Button(this);clear.setText("Убрать напоминание");clear.setOnClickListener(v->{due[0]=0;date.setText("Назначить напоминание");});form.addView(clear);
  CheckBox done=new CheckBox(this);done.setText("Выполнено");done.setChecked(row.completed);form.addView(done);
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(row.id==null?"Новая задача":"Изменить поручение").setView(scroll).setPositiveButton("Сохранить",null).setNeutralButton("Скопировать",(d,w)->{android.content.ClipboardManager cm=getSystemService(android.content.ClipboardManager.class);cm.setPrimaryClip(android.content.ClipData.newPlainText("Поручение",text.getText().toString()));}).setNegativeButton("Назад",null).create();
  dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String t=text.getText().toString().trim();if(t.isEmpty()){text.setError("Введи поручение");return;}if(!done.isChecked()&&due[0]>0&&due[0]<System.currentTimeMillis()){Toast.makeText(this,"Выбери будущее время или убери напоминание",Toast.LENGTH_LONG).show();return;}
   try(TaskDb db=new TaskDb(this)){if(row.id==null){row.id=db.addReminder(t,due[0]);}db.edit(row.id,t,staff.getText().toString().trim(),due[0],done.isChecked());}
   ReminderReceiver.schedule(this,row.id,done.isChecked()?0:due[0]);getSystemService(NotificationManager.class).cancel(row.id,2);SyncEngine.kick(this);dialog.dismiss();refresh();}));dialog.show();Ui.polish(form);
 }
}
