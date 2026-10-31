package ru.secondmemory;
import java.time.*;
/** Local calendar dates, including overnight sessions and daylight-saving changes. */
final class RecallWindow {
 final LocalDate day;final long start,end;
 RecallWindow(LocalDate day,ZoneId zone){this.day=day;start=day==null?Long.MIN_VALUE:day.atStartOfDay(zone).toInstant().toEpochMilli();end=day==null?Long.MAX_VALUE:day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();}
 boolean contains(long recorded){return recorded>=start&&recorded<end;}
}
