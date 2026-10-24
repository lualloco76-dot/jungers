package co.com.jongers.print;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

// Credencial de la terminal protegida con Android Keystore (sin copia de seguridad).
final class SecureStore {
    private static final String ALIAS = "jongers_terminal_aes_v1";
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("jongers_device_local", Context.MODE_PRIVATE);
    }
    static void save(Context context, String token) throws Exception {
        if (!token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Clave inválida: debe tener 64 caracteres hexadecimales.");
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            kg.generateKey();
        }
        SecretKey key = ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        String iv = Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String data = Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        prefs(context).edit().putString("terminal_iv",iv).putString("terminal_encrypted",data).apply();
    }
    static String read(Context context) throws Exception {
        SharedPreferences p=prefs(context);
        String iv=p.getString("terminal_iv", ""), enc=p.getString("terminal_encrypted", "");
        if (iv.isEmpty() || enc.isEmpty()) return "";
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        SecretKey key=((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(iv,Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(enc,Base64.DEFAULT)),StandardCharsets.UTF_8);
    }
    static SharedPreferences publicPrefs(Context context) { return prefs(context); }
}
