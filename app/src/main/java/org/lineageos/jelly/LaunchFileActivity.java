package org.lineageos.jelly;

import android.Manifest;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.util.Base64;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.lineageos.jelly.utils.TabUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

public class LaunchFileActivity extends AppCompatActivity {

    private String url;
    private Intent intent;
    private int emlPart;
    private FileOutputStream fos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intent = getIntent();

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_SEND)) {
                url = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (url == null) {
                    urlCacheLocalUri((Uri) intent.getExtras().get(Intent.EXTRA_STREAM));
                    finish();
                }
                int indexOfUrl = url.toLowerCase().indexOf("http");
                if (indexOfUrl == -1)
                    finish();
                else {
                    String containsURL = url.substring(indexOfUrl);

                    int endOfUrl = containsURL.indexOf(" ");

                    if (endOfUrl != -1) {
                        url = containsURL.substring(0, endOfUrl);
                    } else {
                        url = containsURL;
                    }
                }
                TabUtils.openInNewTab(this, url, true);
            }else if (intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)
                    && intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) != null){
                TabUtils.openInNewTab(this, intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT), true);
            }else if (intent.getAction().equals(Intent.ACTION_WEB_SEARCH)
                    && intent.getStringExtra(SearchManager.QUERY) != null){
                TabUtils.openInNewTab(this, intent.getStringExtra(SearchManager.QUERY), true);
            }else if (intent.getBooleanExtra("kill_all", false)) {
                TabUtils.killAll(getApplicationContext());
            } else if (intent.getScheme() != null &&
                    (intent.getScheme().equals("content")
                            || intent.getScheme().equals("file"))) {
                if (intent.getScheme().equals("content")
                        || Objects.requireNonNull(intent.getDataString()).endsWith(".eml")
                        //|| (intent.getType().equals("message/rf822"))
                ) {
                    urlCacheLocalUri(intent.getData());
                } else {
                    url = intent.getDataString();
                }
                if (!hasStoragePermissionRead()) {
                    //finish();
                } else {
                    Toast.makeText(this, "permission READ_storage granted", Toast.LENGTH_LONG).show();
                }
                //TabUtils.openInNewTab(this, url, true);
            }
        }
        finish();
    }

    private void urlCacheLocalUri(Uri uri) {
        String sMime="";
        boolean bEml = false;
        if (intent.getDataString()== null || !intent.getDataString().substring(intent.getDataString().lastIndexOf("/")).contains(".")) {
            if (getContentResolver().getType(uri) != null) {
                sMime = "."+MimeTypeMap.getSingleton().getExtensionFromMimeType(getContentResolver().getType(uri));
                if (sMime.equals(".bin")) sMime = mimeHead(uri);
                else if (sMime.equals(".null")) {
                    int i = Objects.requireNonNull(getContentResolver().getType(uri)).indexOf("/");
                    // if (i==0) sMime = "."; else
                    sMime = "." + Objects.requireNonNull(getContentResolver().getType(uri)).substring(i+1);
                    if (sMime.equals(".*")) sMime = mimeHead(uri);
                } else if (sMime.equals(".eml")) {
                    sMime = ".html";
                    bEml = true;
                }
            } else sMime = mimeHead(uri);
        } else if (intent.getDataString().endsWith(")")) sMime = mimeHead(uri);
        if (uri.toString().endsWith(".eml") || mimeHead(uri).equals(".eml")) {
            sMime = ".html";
            bEml  =true;
        }

        File f = new File(getBaseContext().getCacheDir(), uri.getLastPathSegment().replace(":", "").replace("/", ".")
                + sMime);
        try {
            fos = new FileOutputStream(f);
            InputStream input = getBaseContext().getContentResolver().openInputStream(uri);

            if (bEml) {
                Properties props = System.getProperties();
                props.put("mail.host", "smtp.dummydomain.com");
                props.put("mail.transport.protocol", "smtp");

                Session mailSession = Session.getDefaultInstance(props, null);
                MimeMessage message = new MimeMessage(mailSession, input);
                rfcHead(message , mailSession);
                fos.flush();
                fos.close();
            } else {
                byte[] buffer = new byte[1024 * 4];
                int n = 0;
                while (-1 != (n = input.read(buffer))) {
                    fos.write(buffer, 0, n);
                }
            }
        } catch (IOException | NullPointerException | MessagingException e) {
            //Log.e("errro", e.toString());
        }
        url = "file:///" + f.getPath();

    }
    private void htmlWrite(int i, String s) {
        String sTab = "";
        if (i>0) sTab = new String(new char[i-1]).replace("\0", "===");
        try {
            fos.write((Html.toHtml((new SpannableString(sTab  + s+ "\n"))).getBytes(Charset.forName("UTF-8"))));
        } catch (IOException e){
            //Log.e("errro", e.toString());
        }
    }

    private String beforeS(String src, String s) {
        return src.contains(s) ? src.substring(0, src.indexOf(s)): src;
    }

    private void rfcHead(MimeMessage message, Session mailSession) {
        try {
            htmlWrite(0, "$$$ : " + message.getDescription());
            htmlWrite(0, "SUBJECT : " + message.getSubject());
            htmlWrite(0, "FROM : " + message.getFrom()[0]);
            htmlWrite(0, "REPLYTO : " + message.getReplyTo()[0]);
            htmlWrite(0, "BODY : " +  beforeS(message.getContentType(), ";"));

            if (message.getContentType().startsWith("multipart")) {
                Multipart multiPart = (Multipart) message.getContent();
                pp(multiPart, ">> ", mailSession);
            } else htmlWrite(0, "--------------");


        } catch ( IOException | MessagingException e) {
            //Log.e("errro", e.toString());
        }
    }
    private void pp(Multipart multiPart, String s, Session mailSession){
        emlPart += 1;
        String emlTab = new String(new char[emlPart]).replace("\0", s);
        try{
            int numberOfParts = multiPart.getCount();
            htmlWrite(0, "\n\n");
            htmlWrite(emlPart, emlTab + "--------------MULTIPART EMAIL:Parts="+numberOfParts);
            for (int partCount = 0; partCount < numberOfParts; partCount++) {
                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
                htmlWrite(emlPart, emlTab + "°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°°");
                htmlWrite(emlPart, emlTab + "Part type::"+beforeS(part.getContentType(), ";"));
                htmlWrite(emlPart, emlTab + "Part Name::"+part.getFileName());
                htmlWrite(emlPart, emlTab + "Part Description::"+part.getDescription());
                htmlWrite(emlPart, emlTab + "Part Disposition::"+part.getDisposition());
                htmlWrite(emlPart, emlTab + "Part Encoding::"+part.getEncoding());
                if (part.getContentType().startsWith("multipart")) {
                    Multipart sub = (Multipart) part.getContent();
                    pp(sub, ">>>> ", mailSession);

                } else if (part.getContentType().startsWith("message/rfc822")) {
                    rfcHead((MimeMessage) part.getContent(), mailSession);
                } else if (part.getContentType().startsWith("text/html")) {
                    fos.write((part.getLineCount()+part.getContent().toString()).getBytes(Charset.forName("UTF-8")));
                } else if (part.getContentType().startsWith("image") && part.getEncoding().equals("base64")) {
                    fos.write(("<img src='data:"+ beforeS(part.getContentType(), ";") +";" + part.getEncoding() + "," ).getBytes(Charset.forName("UTF-8")));
                    InputStream input = part.getInputStream();
                    byte[] buffer = new byte[4096];
                    ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                    int byteRead;
                    while ((byteRead = input.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, byteRead);
                    }
                    fos.write(Base64.encodeToString(byteBuffer.toByteArray(),Base64.DEFAULT).getBytes(Charset.forName("UTF-8")));
                    fos.write(("'>").getBytes(Charset.forName("UTF-8")));
                }
                htmlWrite(emlPart, "...");
            }

        } catch (IOException | MessagingException e) {
            //Log.e("errro", e.toString());
        }
        htmlWrite(0, "\n\n");
        emlPart -= 1;
    }

    private String mimeHead(Uri uri) {
        try {
            byte[] buffer = new byte[1024];
            getBaseContext().getContentResolver().openInputStream(uri).read(buffer);
            String sHead = new String(buffer, StandardCharsets.UTF_8);
            if (sHead.contains("\nContent-Transfer-Encoding: quoted-printable"))
                return ".mht";
            if (sHead.contains("\nContent-Transfer-Encoding: binary"))
                return ".htm";
            if (sHead.contains("\nContent-Type: multipart/mixed;"))
                return ".eml";

            sHead = sHead.toUpperCase();
            if (sHead.startsWith("<!DOCTYPE HTML"))
                return ".htm";
            if (sHead.startsWith("<?XML") && sHead.contains("\n<SVG"))
                return ".svg";
            if (sHead.startsWith("<?XML"))
                return ".xml";
            if (sHead.contains("\n<!DOCTYPE HTML"))
                return ".htm";
        } catch (IOException | NullPointerException e) {
            //Log.e("errro", e.toString());
            return e.toString();
        }
        return ".";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "permission validation", Toast.LENGTH_LONG).show();
            TabUtils.openInNewTab(this, url, true);
        } else {
            Toast.makeText(this, "permission READ_storage DENIED", Toast.LENGTH_LONG).show();
            ActivityCompat.finishAffinity(this);
        }
    }

    private boolean hasStoragePermissionRead() {
        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            return false;
        } else {
            TabUtils.openInNewTab(this, url, true);
        } return true;
    }
}
