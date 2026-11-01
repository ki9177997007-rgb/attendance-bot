package ru.secondmemory;

import java.time.*;
import java.util.regex.*;

/** Deterministic commands. Conversation audio requires the explicit wake prefix. */
final class PhoneCommand {
 enum Kind { ALARM, TIMER, OPEN, REMINDER }
 final Kind kind; final String text, target; final long value;
 PhoneCommand(Kind k,String t,String x,long v){kind=k;text=t;target=x;value=v;}
 static boolean addressed(String text){return TaskRules.normalize(text).matches("^(?:помощник|память|вторая память) .+");}
 static PhoneCommand parse(String text,long now,ZoneId zone){
  String s=TaskRules.normalize(text).replaceFirst("^(?:вторая память|память|помощник) +","").replaceFirst("^пожалуйста +","");
  if(s.matches(".*\\b(например|если|допустим|отмени|отменить)\\b.*")||s.startsWith("не "))throw new IllegalArgumentException("Скажи одну прямую команду без отрицания и примеров.");
  if(s.matches("^(?:(?:поставь|установи|включи) )?таймер .+")){
   String duration=DueParser.numbers(s.replaceFirst("^(?:(?:поставь|установи|включи) )?таймер +","").replaceFirst("^(?:на|через) +",""));
   duration=duration.replace("полчаса","30 минут").replaceAll("^час$","1 час").replaceAll("^минуту$","1 минуту").replaceAll("^секунду$","1 секунду");
   Matcher m=Pattern.compile("(\\d{1,4}) (час(?:а|ов)?|минут(?:а|у|ы)?|секунд(?:а|у|ы)?)\\b").matcher(duration);
   long total=0;int end=0;boolean found=false;
   while(m.find()){if(!duration.substring(end,m.start()).trim().matches("(?:и)?"))throw new IllegalArgumentException("Укажи длительность таймера: например, на 20 минут.");long n=Long.parseLong(m.group(1));total+=n*(m.group(2).startsWith("час")?3600:m.group(2).startsWith("минут")?60:1);end=m.end();found=true;}
   if(!found||!duration.substring(end).trim().isEmpty()||total<=0||total>86400)throw new IllegalArgumentException("Длительность таймера — от 1 секунды до 24 часов.");
   return new PhoneCommand(Kind.TIMER,text,"Таймер",total);
  }
  if(s.matches("^(?:(?:поставь|установи|включи) )?будильник .+")){
   String time=s.replaceFirst("^(?:(?:поставь|установи|включи) )?будильник +","").replaceFirst("^на +","в ");
   if(!time.matches(".*\\b(?:в|к) .+"))time="в "+time;
   long due=DueParser.parse(time,now,zone);
   if(due==0&&!time.matches(".*\\b(сегодня|завтра|послезавтра|понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье|января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\b.*"))due=DueParser.parse("завтра "+time,now,zone);
   if(due<=now||due-now>86400000L)throw new IllegalArgumentException("Для будильника укажи время ближайших суток, например: завтра в 6:30. Для другой даты используй напоминание.");
   return new PhoneCommand(Kind.ALARM,text,"Будильник",due);
  }
  if(s.startsWith("открой ")||s.startsWith("запусти ")){
   String target=s.substring(s.indexOf(' ')+1).trim();if(target.isEmpty())throw new IllegalArgumentException("Назови приложение.");
   return new PhoneCommand(Kind.OPEN,text,target,0);
  }
  if(s.startsWith("напомни ")||s.startsWith("напоминание ")){
   long due=DueParser.parse(s,now,zone);if(due<=now)throw new IllegalArgumentException("Укажи будущее время: напомни через 10 минут позвонить или завтра в 8:30.");
   return new PhoneCommand(Kind.REMINDER,text,s,due);
  }
  throw new IllegalArgumentException("Примеры: поставь будильник на 6:30; таймер на 20 минут; открой Telegram; напомни через 10 минут позвонить.");
 }
 String summary(){if(kind==Kind.OPEN)return "Открыть: "+target;if(kind==Kind.TIMER)return "Таймер: "+value+" сек.";return (kind==Kind.ALARM?"Будильник: ":"Напоминание: ")+Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))+(kind==Kind.REMINDER?"\n"+target:"");}
}
