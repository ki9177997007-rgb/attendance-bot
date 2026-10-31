package ru.secondmemory;
import android.database.*;
import android.os.*;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.Arrays;

/** Read-only SAF surface; never exposes credentials, voice profiles or raw speaker vectors. */
public class RecordingsProvider extends DocumentsProvider {
    static final String AUTHORITY=BuildConfig.APPLICATION_ID+".recordings";
    private static final String[] DOC={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED};
    @Override public boolean onCreate(){return true;}
    private File base(){File f=new File(getContext().getFilesDir(),"sessions");f.mkdirs();return f;}
    private File resolve(String id) throws FileNotFoundException {
        try {
            File root=base().getCanonicalFile(),f=id.equals("root")?root:new File(root,id).getCanonicalFile();
            if(!f.equals(root)&&!f.getPath().startsWith(root.getPath()+File.separator))throw new FileNotFoundException();
            if(!f.exists()||(!f.isDirectory()&&!f.getName().endsWith(".txt")&&!f.getName().endsWith(".wav")))throw new FileNotFoundException();
            return f;
        }catch(IOException e){throw new FileNotFoundException("Запись недоступна");}
    }
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols=projection==null?new String[]{Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS,Root.COLUMN_MIME_TYPES}:projection;
        MatrixCursor c=new MatrixCursor(cols);MatrixCursor.RowBuilder r=c.newRow();
        for(String col:cols) {
            Object value=null;
            if(col.equals(Root.COLUMN_ROOT_ID)||col.equals(Root.COLUMN_DOCUMENT_ID))value="root";
            else if(col.equals(Root.COLUMN_TITLE))value="Вторая память 0.8";
            else if(col.equals(Root.COLUMN_FLAGS))value=Root.FLAG_LOCAL_ONLY;
            else if(col.equals(Root.COLUMN_MIME_TYPES))value="text/plain\naudio/wav";
            r.add(value);
        }
        return c;
    }
    private void add(MatrixCursor cursor,File file) {
        String id=file.equals(base())?"root":base().toURI().relativize(file.toURI()).getPath();
        MatrixCursor.RowBuilder row=cursor.newRow();
        for(String col:cursor.getColumnNames()) {
            Object value=null;
            if(col.equals(Document.COLUMN_DOCUMENT_ID))value=id;
            else if(col.equals(Document.COLUMN_DISPLAY_NAME))value=id.equals("root")?"Вторая память 0.8":file.getName();
            else if(col.equals(Document.COLUMN_MIME_TYPE))value=file.isDirectory()?Document.MIME_TYPE_DIR:file.getName().endsWith(".wav")?"audio/wav":"text/plain";
            else if(col.equals(Document.COLUMN_SIZE))value=file.length();
            else if(col.equals(Document.COLUMN_FLAGS))value=0;
            else if(col.equals(Document.COLUMN_LAST_MODIFIED))value=file.lastModified();
            row.add(value);
        }
    }
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?DOC:projection);add(c,resolve(id));return c;}
    @Override public Cursor queryChildDocuments(String id,String[] projection,String order)throws FileNotFoundException {
        MatrixCursor c=new MatrixCursor(projection==null?DOC:projection);File[] files=resolve(id).listFiles();
        if(files!=null){Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));for(File f:files)if(f.isDirectory()||f.getName().endsWith(".txt")||f.getName().endsWith(".wav"))add(c,f);}return c;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Только чтение");
        File f=resolve(id);if(f.isDirectory())throw new FileNotFoundException();return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
