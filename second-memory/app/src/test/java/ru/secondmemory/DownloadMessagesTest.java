package ru.secondmemory;
import org.junit.Test;import static org.junit.Assert.*;
public class DownloadMessagesTest {
 @Test public void tellsUserWhyDownloadFailed(){assertTrue(DownloadMessages.error(403).contains("HTTP 403"));assertTrue(DownloadMessages.error(1006).contains("места"));assertTrue(DownloadMessages.error(1005).contains("браузер"));}
 @Test public void explainsWifiWaitInsteadOfPretendingProgress(){assertTrue(DownloadMessages.paused(3).contains("Wi-Fi"));assertTrue(DownloadMessages.paused(2).contains("сети"));}
}
