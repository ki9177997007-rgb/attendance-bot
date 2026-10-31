package ru.secondmemory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class YouGileApi {
    interface Transport { JSONObject call(String method,String path,JSONObject body,String key) throws Exception; }
    private final Transport transport;
    private final String key;
    YouGileApi(String key) { this(key,YouGileApi::http); }
    YouGileApi(String key,Transport transport) { this.key=key;this.transport=transport; }
    JSONObject call(String method,String path,JSONObject body) throws Exception { return transport.call(method,path,body,key); }
    private static JSONObject http(String method,String path,JSONObject body,String key) throws Exception {
        if(!path.startsWith("/api-v2/"))throw new IOException("Недопустимый адрес API");
        HttpURLConnection connection=(HttpURLConnection)new URL("https://yougile.com"+path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
        connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");
        if(key!=null)connection.setRequestProperty("Authorization","Bearer "+key);
        try {
            if(body!=null) {
                connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            }
            int status=connection.getResponseCode();
            if(status<200||status>=300)throw new IOException(status==401||status==403?"YouGile: проверь вход и права доступа ("+status+")": "YouGile: ошибка HTTP "+status);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){output.write(buffer,0,n);if(output.size()>4*1024*1024)throw new IOException("Слишком большой ответ YouGile");}
                String text=output.toString("UTF-8");return text.trim().isEmpty()?new JSONObject():new JSONObject(text);
            }
        } finally { connection.disconnect(); }
    }
    JSONObject setup() throws Exception {
        String user=call("GET","/api-v2/users/me",null).getString("id");
        String prefix="second-memory-v1-"+user;
        String project=call("POST","/api-v2/projects",new JSONObject().put("title","Вторая память")
            .put("users",new JSONObject().put(user,"admin")).put("idempotencyKey",prefix+"-project")).getString("id");
        String board=call("POST","/api-v2/boards",new JSONObject().put("title","Поручения из разговоров").put("projectId",project).put("idempotencyKey",prefix+"-board")).getString("id");
        String inbox=column(board,"Приоритет — проверить",prefix+"-inbox");
        String review=column(board,"Проверить говорящего",prefix+"-review");
        return new JSONObject().put("key",key).put("project",project).put("board",board).put("inbox",inbox).put("review",review);
    }
    private String column(String board,String title,String id) throws Exception {
        return call("POST","/api-v2/columns",new JSONObject().put("title",title).put("boardId",board).put("idempotencyKey",id)).getString("id");
    }
    String send(TaskDb.Row row,JSONObject config) throws Exception {
        return call("POST","/api-v2/tasks",payload(row,config)).getString("id");
    }
    static String description(TaskDb.Row row){return "Черновик поручения из речи. Проверь распознавание, исполнителя и срок.\n"+row.text+"\nИсполнитель: "+(row.assignee==null?"":row.assignee)+"\nСрок: "+(row.due==0?"не задан":new java.util.Date(row.due))+"\nЛокальная запись: "+row.session;}
    static JSONObject payload(TaskDb.Row row,JSONObject config) throws JSONException {
        return new JSONObject().put("title",TaskRules.title(row.text))
            .put("columnId",config.getString(row.priority?"inbox":"review"))
            .put("description",description(row))
            .put("idempotencyKey","second-memory-task-"+row.id)
            .put("apiData",new JSONObject().put("secondMemoryId",row.id))
            .put("completed",row.completed);
    }
}
