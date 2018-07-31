package com.example.anarg.logreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import tgio.rncryptor.RNCryptorNative;

public class MainActivity extends AppCompatActivity implements ThreadCompleteInterface {

    private static final String folderPath= Environment.getExternalStorageDirectory().getAbsolutePath()+"/.FogSignal";
    private static final String logFolderPath= Environment.getExternalStorageDirectory().getAbsolutePath()+"/FogSignalLogs";
    private static final String decryptionPassword="sgEAafvWVVepbusYGGKFYCCxztKuqFdVHrjtAacugcaenPaTjcyMaHZXrgmCTHpD";
    private TextView status;
    private File hiddenFolder;
    private int newFilesWritten;
    private boolean fileIOPermission;
    private HashMap<File,Integer> modifiedFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hiddenFolder=new File(folderPath);
        fileIOPermission=false;
        if (!hiddenFolder.exists()){
            exceptionRaised("Error","No logs created yet! Make sure Fog Signal app ran " +
                    "at least once or the encrypted log folder in copied to your internal storage of " +
                    "the pone.",true);
        }else{
            if (!fileIOPermission) {
                askPermission(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE, 1);
            }
            setContentView(R.layout.activity_main);
            modifiedFiles=new HashMap<>();
            status=findViewById(R.id.statusView);
        }
    }

    public void decryptButton(View view) {
        status.setText("Process Started.....");
        newFilesWritten=0;
        modifiedFiles.clear();
        ThreadCompleteInterface t=this;
        final Runnable readFiles= new Runnable() {
                String statusString = "";
                boolean val;
                @Override
                public void run() {
                    try {
                        val = operation();
                        if (!modifiedFiles.isEmpty()){
                            statusString+=modifiedMessage();
                        }
                        if (val) {
                            statusString+="Operation Completed!\n"+newFilesWritten+" File(s) were " +
                                    "created/changed!\nLocation: "+logFolderPath;
//                        status.setText(statusString);
                        }else{
                            statusString+="No new Files Written!";
//                        status.setText(statusString);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
//                    status.setText("Error! :(");
                    }finally {
                        t.notifyOfThreadComplete(statusString);
                    }
                }
            };
            new Thread(readFiles).start();
//            Log.d("AppTest", Integer.toString(countLines(folderPath+"/.37855.log")));
//            Log.d("AppTest", Integer.toString(countLines(logFolderPath+"/37855.log")))
    }

    @Override
    public void notifyOfThreadComplete(String statusString) {
        runOnUiThread(() -> {

            // Stuff that updates the UI
            status.setText(statusString);

        });
    }

    private String modifiedMessage(){
        StringBuilder res= new StringBuilder();
        for (File f: modifiedFiles.keySet()){
            res.append(f.getName()).append(", Incorrectly Modified Lines: ").append(modifiedFiles.get(f)).append("\n");
        }
        return String.valueOf(res);
    }

    private boolean operation() throws IOException {
        File[] allFiles=hiddenFolder.listFiles();
        ArrayList<HashMap<String,String>> data=new ArrayList<>();
        for (File f: allFiles){
            HashMap<String,String> value=readFileContents(f);
            if (!value.isEmpty()) {
                data.add(value);
            }
        }
       return writeToFile(data);
    }

    private boolean writeToFile(ArrayList<HashMap<String,String>> data) throws IOException {
        boolean written=false;
        File logFolder = new File(logFolderPath);
        FileWriter logFile;
        if (!logFolder.exists()) {
            logFolder.mkdir();
        }
        for (int i = 0; i < data.size(); i++) {
            HashMap<String,String> val=data.get(i);
            String fileName=getFileName(val);
            File file = new File(logFolder, fileName+".csv");
            if (!file.exists()) {
                logFile = new FileWriter(file);
                writeHelper(logFile,val.get(fileName));
                written=true;
            }else{
                if (countLines(logFolderPath+"/"+fileName+".csv")!=countLines(folderPath+"/."+fileName)+1){
                    logFile = new FileWriter(file);
                    writeHelper(logFile,val.get(fileName));
                    written=true;
                }
            }
        }
        return written;
    }

    private void writeHelper(FileWriter logFile,String FileName) throws IOException {
        String s = "Time,Train Name,Train Number,Track Name,Signal ID,Signal Aspect\n";
        logFile.write(s);
        logFile.write(FileName);
        logFile.close();
        newFilesWritten++;
    }

    private String getFileName(HashMap<String,String> a){
        String res = "";
        for(String s: a.keySet()){
            res=s;
        }
        return res;
    }

    private HashMap<String,String> readFileContents(File f) throws IOException {
        HashMap<String,String> data=new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(f));
        StringBuilder text = new StringBuilder();
        String line;
        int changedLines=0;
        while ((line = br.readLine()) != null) {
            String value=decryptedString(line);
            if (value.isEmpty()){
                changedLines++;
                modifiedFiles.put(f,changedLines);
            }else {
                text.append(value);
                text.append('\n');
            }
        }
        String newName=f.getName().substring(1,f.getName().length());
        String finalValue=String.valueOf(text);
        if (!finalValue.isEmpty()) {
            data.put(newName,finalValue);
        }
        return data;
    }
    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    private String decryptedString(String encryptedString){
        final String[] res = new String[1];
        RNCryptorNative.decryptAsync(encryptedString, decryptionPassword, (result, e) -> res[0] =result);
        return res[0];
    }

    public void exceptionRaised(String title,String body,boolean buttons) {
        AlertDialog.Builder builder=new AlertDialog.Builder(this);
        builder.setMessage(body)
                .setTitle(title);
        if (buttons) {
            builder.setNegativeButton("Restart", (dialog, which) -> {
                finish();
                Intent i = getIntent();
                startActivity(i);
            });
            builder.setPositiveButton("Exit", (dialog, which) -> finish());
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
    }
    private void askPermission(String permission,String permission2, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission,permission2}, requestCode);
        } else {
            fileIOPermission = true;
            Log.d("FIle", Boolean.toString(fileIOPermission));
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Granted!",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(this,"Enable it!",Toast.LENGTH_SHORT).show(); //change it later
                }
        }
    }
    public static int countLines(String filename) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(filename))) {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        }
    }

}
