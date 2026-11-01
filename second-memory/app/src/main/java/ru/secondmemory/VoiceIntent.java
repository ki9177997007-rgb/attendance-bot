package ru.secondmemory;
import java.time.*;
/** Small explicit offline grammar. Unknown speech never mutates a task. */
final class VoiceIntent {
 enum Kind { LIST, RECALL, DONE, SNOOZE, REMINDER, REPEAT, HELP, QUIET, UNKNOWN }
 final Kind kind; final String text; final int ordinal;
 VoiceIntent(Kind k,String t,int n){kind=k;text=t;ordinal=n;}
 static String body(String text){return TaskRules.normalize(text).replaceFirst("^(?:вторая память|память|помощник) +", "").replaceFirst("^пожалуйста +", "");}
 static int ordinal(String s){String[] names={"перв(?:ое|ую|ый|ый пункт)","втор(?:ое|ую|ой)","трет(?:ье|ью|ий)","четверт(?:ое|ую|ый)","пят(?:ое|ую|ый)"};for(int i=0;i<5;i++)if(s.matches(".*\\b(?:"+names[i]+"|"+(i+1)+")\\b.*"))return i;return -1;}
 static VoiceIntent parse(String raw){String s=body(raw);Kind k=Kind.UNKNOWN;int n=ordinal(s);
  if(s.matches(".*\\b(если|например|допустим)\\b.*")||s.startsWith("не "))return new VoiceIntent(k,s,n);
  if(s.matches("(?:что|че|чего) (?:мне )?(?:там )?(?:надо |нужно )?(?:сделать|делать)(?: дальше| сегодня)?")||s.matches("(?:мои (?:дела|задачи|поручения)|список (?:дел|задач)|что по задачам)"))k=Kind.LIST;
  else if(s.matches(".*\\b(сделал|сделано|выполнил|выполнено|готово|заверши|закрой)\\b.*")&&n>=0&&s.matches("^(?:(?:я |уже )?(?:первое|первую|первый|второе|вторую|второй|третье|третью|третий|четвертое|четвертую|четвертый|пятое|пятую|пятый|[1-5]) .+|(?:закрой|заверши) .+)"))k=Kind.DONE;
  else if(s.matches(".*\\b(перенеси|отложи|перенос|позже)\\b.*")&&n>=0&&s.matches("^(?:перенеси|отложи) .+"))k=Kind.SNOOZE;
  else if(s.startsWith("напомни ")||s.startsWith("напоминание "))k=Kind.REMINDER;
  else if(s.matches("(?:повтори|повтори список|еще раз)"))k=Kind.REPEAT;
  else if(s.matches("(?:помощь|что ты умеешь|какие команды)"))k=Kind.HELP;
  else if(s.matches("(?:тихо|замолчи|хватит|стоп)"))k=Kind.QUIET;
  else if(s.matches("^(?:вспомни|найди|помнишь|о чем|что мы|что говорили|что обсуждали).+"))k=Kind.RECALL;
  return new VoiceIntent(k,s,n);
 }
 static long[] window(String query,long now,ZoneId zone){String s=TaskRules.normalize(query);LocalDate day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();if(s.matches(".*\\bвчера\\b.*"))day=day.minusDays(1);else if(!s.matches(".*\\bсегодня\\b.*"))return new long[]{Long.MIN_VALUE,Long.MAX_VALUE};return new long[]{day.atStartOfDay(zone).toInstant().toEpochMilli(),day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()};}
}
