// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.io.*;
import org.json.JSONObject;
final class LifeSessionVault {
    private static final String ALIAS="campuskit-life798-v1";
    private final AtomicFile file;
    LifeSessionVault(Context c){file=new AtomicFile(new File(c.getNoBackupFilesDir(),"life798-session.bin"));}
    private SecretKey key(boolean create)throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);SecretKey key=(SecretKey)store.getKey(ALIAS,null);if(key==null&&create){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes("GCM").setEncryptionPaddings("NoPadding").setKeySize(256).build());key=gen.generateKey();}return key;}
    synchronized void save(JSONObject session)throws Exception{session=LifeAuthApi.credentials(session);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(true));byte[] encrypted=c.doFinal(session.toString().getBytes("UTF-8"));FileOutputStream out=null;try{out=file.startWrite();out.write(c.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}
    synchronized boolean exists()throws Exception{return load()!=null;}
    synchronized JSONObject load()throws Exception{byte[] bytes;try{bytes=TimetableBackup.read(file.openRead(),16384);}catch(FileNotFoundException e){return null;}if(bytes.length<29)throw new IOException();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,bytes,0,12));JSONObject data=new JSONObject(new String(c.doFinal(bytes,12,bytes.length-12),"UTF-8"));return LifeAuthApi.credentials(data);}
    synchronized void clear()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);file.delete();}
}
