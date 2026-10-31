package ru.secondmemory;
import java.time.*;
import java.util.regex.*;
/** Alarm dates come only from explicit supported wording, never model-generated dates. */
final class DueParser {
 static String numbers(String text){
  String s=TaskRules.normalize(text);
  String[] declined={"одному","двум","трем","четырем","пяти","шести","семи","восьми","девяти","десяти","одиннадцати","двенадцати","тринадцати","четырнадцати","пятнадцати","шестнадцати","семнадцати","восемнадцати","девятнадцати"};
  for(int n=0;n<declined.length;n++)s=s.replaceAll("\\b"+declined[n]+"\\b",String.valueOf(n+1));
  String[] small={"ноль","один","два","три","четыре","пять","шесть","семь","восемь","девять","десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать"};
  s=s.replaceAll("\\bодну\\b","один").replaceAll("\\bодна\\b","один").replaceAll("\\bдве\\b","два");
  String[] tens={"двадцать","тридцать","сорок","пятьдесят"};
  for(int t=0;t<tens.length;t++){for(int n=1;n<10;n++)s=s.replaceAll("\\b"+tens[t]+" "+small[n]+"\\b",String.valueOf((t+2)*10+n));s=s.replaceAll("\\b"+tens[t]+"\\b",String.valueOf((t+2)*10));}
  for(int n=0;n<small.length;n++)s=s.replaceAll("\\b"+small[n]+"\\b",String.valueOf(n));return s;
 }
 static long parse(String text,long now,ZoneId zone) {
  String s=numbers(text).replaceAll("через час\\b","через 1 час").replaceAll("через минуту\\b","через 1 минуту");ZonedDateTime base=Instant.ofEpochMilli(now).atZone(zone);
  if(s.contains("через полчаса"))return now+1800000L;
  Matcher delay=Pattern.compile("через (\\d{1,3}) (минут[уы]?|час(?:а|ов)?)\\b").matcher(s);
  if(delay.find()){
   long n=Long.parseLong(delay.group(1));if(n==0)return 0;long duration=n*(delay.group(2).startsWith("час")?3600000L:60000L);
   if(delay.group(2).startsWith("час")){Matcher extra=Pattern.compile("^ (\\d{1,2}) минут[уы]?\\b").matcher(s.substring(delay.end()));if(extra.find()){int minutes=Integer.parseInt(extra.group(1));if(minutes>59)return 0;duration+=minutes*60000L;}}
   return now+duration;
  }
  Matcher time=Pattern.compile("(?:в|к) (\\d{1,2})(?: (\\d{2}))?(?: час(?:а|ов|ам)?)?(?:$| )").matcher(s);
  int h=-1,m=0;
  while(time.find()){
   if(s.substring(time.end()).matches("^(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря).*"))continue;
   h=Integer.parseInt(time.group(1));m=time.group(2)==null?0:Integer.parseInt(time.group(2));break;
  }
  if(h<0)return 0;
  if((s.contains("вечера")||s.contains("дня"))&&h<12)h+=12;
  if(s.contains("ночи")&&h==12)h=0;
  if(h>23||m>59)return 0;
  LocalDate date=base.toLocalDate();
  String[] days={"понедельник","вторник","среду","четверг","пятницу","субботу","воскресенье"};
  String[] months={"января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"};
  if(s.contains("послезавтра"))date=date.plusDays(2);else if(s.contains("завтра"))date=date.plusDays(1);
  else for(int d=0;d<days.length;d++)if(s.contains(days[d])){int delta=(d+1-date.getDayOfWeek().getValue()+7)%7;if(delta==0&&(s.contains("следующ")||!date.atTime(h,m).atZone(zone).isAfter(base)))delta=7;date=date.plusDays(delta);break;}
  for(int month=0;month<months.length;month++)if(s.contains(months[month])){
   Matcher md=Pattern.compile("(\\d{1,2}) "+months[month]+"(?: (20\\d{2}))?").matcher(s);
   if(!md.find())return 0;
   try{date=LocalDate.of(md.group(2)==null?base.getYear():Integer.parseInt(md.group(2)),month+1,Integer.parseInt(md.group(1)));}catch(DateTimeException e){return 0;}
  }
  ZonedDateTime due=date.atTime(h,m).atZone(zone);return due.isAfter(base)?due.toInstant().toEpochMilli():0;
 }
}
