package ru.secondmemory;
import java.time.*;
import java.util.regex.*;
/** Conservative fallback: ambiguous dates stay unset, never guessed. */
final class DueParser {
 static long parse(String text,long now,ZoneId zone) {
  String s=TaskRules.normalize(text); ZonedDateTime base=Instant.ofEpochMilli(now).atZone(zone);
  Matcher delay=Pattern.compile("через (\\d{1,3}) (минут[уы]?|час(?:а|ов)?)\\b").matcher(s);
  if(delay.find()){long n=Long.parseLong(delay.group(1));return n>0?now+n*(delay.group(2).startsWith("час")?3600000L:60000L):0;}
  Matcher time=Pattern.compile("(?:в|к) (\\d{1,2})(?: (\\d{2}))?(?: час(?:а|ов)?)?(?:$| )").matcher(s);
  if(!time.find())return 0;
  int h=Integer.parseInt(time.group(1)),m=time.group(2)==null?0:Integer.parseInt(time.group(2));
  if(h>23||m>59)return 0;
  // Do not silently turn an explicit unsupported calendar date into today.
  if(s.matches(".*\\b(понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье|января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\b.*"))return 0;
  LocalDate date=base.toLocalDate();
  if(s.contains("послезавтра"))date=date.plusDays(2);else if(s.contains("завтра"))date=date.plusDays(1);
  ZonedDateTime due=date.atTime(h,m).atZone(zone);
  if(!due.isAfter(base))return 0;
  return due.toInstant().toEpochMilli();
 }
}
