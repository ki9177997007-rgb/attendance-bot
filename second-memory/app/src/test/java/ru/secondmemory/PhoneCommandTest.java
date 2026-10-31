package ru.secondmemory;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class PhoneCommandTest {
 private final ZoneId zone=ZoneId.of("Europe/Moscow");
 private final long now=ZonedDateTime.of(2026,10,31,17,0,0,0,zone).toInstant().toEpochMilli();
 @Test public void alarmsUseNextOccurrenceAndExplicitTomorrow(){
  PhoneCommand c=PhoneCommand.parse("Память, поставь будильник на шесть тридцать",now,zone);
  assertEquals(PhoneCommand.Kind.ALARM,c.kind);assertEquals(ZonedDateTime.of(2026,11,1,6,30,0,0,zone).toInstant().toEpochMilli(),c.value);
  assertEquals(now+2*3600000L,PhoneCommand.parse("будильник на 19:00",now,zone).value);
 }
 @Test public void timerMixedUnitsAndSpokenNumbers(){
  assertEquals(90,PhoneCommand.parse("таймер на одну минуту и тридцать секунд",now,zone).value);
  assertEquals(5400,PhoneCommand.parse("Память таймер на один час тридцать минут",now,zone).value);
  assertEquals(1800,PhoneCommand.parse("таймер на полчаса",now,zone).value);
 }
 @Test public void reminderKeepsOriginalTimeBaseForQueuedCommands(){assertEquals(now+10*60000L,PhoneCommand.parse("Память напомни через десять минут позвонить Олегу",now,zone).value);assertEquals(now+3600000,PhoneCommand.parse("напомни через час позвонить",now,zone).value);}
 @Test public void openingAndWakePrefix(){assertTrue(PhoneCommand.addressed("Вторая память открой Telegram"));assertFalse(PhoneCommand.addressed("нужно открыть Telegram"));assertEquals("telegram",PhoneCommand.parse("Память открой Telegram",now,zone).target);}
 @Test public void refusesPastAmbiguousNegatedAndInvalidDurations(){
  String[] invalid={"не ставь будильник в 6","например таймер на 5 минут","таймер на ноль минут","таймер на 2 часа 5 слонов","будильник сегодня в 16:00","будильник в 25:00","будильник послезавтра в 6:00","напомни завтра позвонить","таймер на 9999 часов"};
  for(String text:invalid){try{PhoneCommand.parse(text,now,zone);fail(text);}catch(IllegalArgumentException expected){}}
 }
}
