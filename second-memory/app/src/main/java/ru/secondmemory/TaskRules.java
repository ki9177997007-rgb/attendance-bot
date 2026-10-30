package ru.secondmemory;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local, deliberately conservative command rules; not semantic LLM extraction. */
final class TaskRules {
    private static final Pattern START = Pattern.compile("^(?:задача|напомни|запиши задачу|нужно|надо|необходимо|поручаю|проверь|подготовь|отправь|позвони|собери|сделай|найди|уточни|сдай|получи|передай|оформи|арт[её]м|олег|валентин)\\b.*");
    static String normalize(String text) {
        return text.toLowerCase(new Locale("ru")).replace('ё','е').replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
    }
    static boolean candidate(String text) {
        String n = normalize(text);
        if (n.length() < 10 || n.split(" ").length < 3) return false;
        if (n.matches(".*\\b(если|возможно|например|допустим|отменяю|отмени)\\b.*")) return false;
        if (n.matches(".*\\bне (нужно|надо|отправляй|звони|делай|передавай)\\b.*")) return false;
        if (n.matches("^(артем|олег|валентин) .*"))
            return n.matches("^(артем|олег|валентин) (пожалуйста )?(проверь|подготовь|отправь|позвони|собери|сделай|найди|уточни|сдай|получи|передай|оформи) .*" );
        return START.matcher(n).matches();
    }
    static String title(String text) { return text.length() > 180 ? text.substring(0,177)+"…" : text; }
}
