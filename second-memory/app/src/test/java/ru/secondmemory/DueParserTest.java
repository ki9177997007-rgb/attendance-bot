package ru.secondmemory;
import org.junit.Test;import static org.junit.Assert.*;import java.time.*;
public class DueParserTest {
 final ZoneId z=ZoneId.of("Europe/Moscow");final long now=ZonedDateTime.of(2026,10,31,17,0,0,0,z).toInstant().toEpochMilli();
 @Test public void intervals(){assertEquals(now+5*60000,DueParser.parse("напомни через 5 минут позвонить",now,z));assertEquals(now+2*3600000,DueParser.parse("напомни через 2 часа",now,z));}
 @Test public void spokenTimes(){assertEquals(now+5*60000,DueParser.parse("напомни через пять минут позвонить",now,z));assertEquals(now+2*3600000,DueParser.parse("напомни сегодня в семь вечера",now,z));assertEquals(ZonedDateTime.of(2026,11,1,19,0,0,0,z).toInstant().toEpochMilli(),DueParser.parse("Сергей подготовь реестр завтра к девятнадцати часам",now,z));}
 @Test public void dateWithoutClockIsNotTime(){assertEquals(0,DueParser.parse("сдать реестр к 1 ноября",now,z));assertEquals(ZonedDateTime.of(2026,11,1,19,0,0,0,z).toInstant().toEpochMilli(),DueParser.parse("сдать реестр к 1 ноября в 19:00",now,z));}
 @Test public void mixedDurationAndSameWeekday(){assertEquals(now+150*60000,DueParser.parse("напомни через два часа тридцать минут",now,z));assertEquals(now+2*3600000,DueParser.parse("в субботу в 19:00 отправить реестр",now,z));}
 @Test public void explicitDate(){assertEquals(ZonedDateTime.of(2026,11,2,19,0,0,0,z).toInstant().toEpochMilli(),DueParser.parse("в понедельник в 19:00 отправить реестр",now,z));assertEquals(ZonedDateTime.of(2026,11,1,19,0,0,0,z).toInstant().toEpochMilli(),DueParser.parse("1 ноября в 19:00 отправить реестр",now,z));}
 @Test public void tomorrow(){assertEquals(ZonedDateTime.of(2026,11,1,19,30,0,0,z).toInstant().toEpochMilli(),DueParser.parse("завтра в 19:30 отправить реестр",now,z));}
 @Test public void ambiguousAndPastStayUnset(){assertEquals(0,DueParser.parse("завтра отправить реестр",now,z));assertEquals(0,DueParser.parse("сегодня в 16:00 отправить реестр",now,z));assertEquals(0,DueParser.parse("сегодня в 25:00 отправить реестр",now,z));}
}
