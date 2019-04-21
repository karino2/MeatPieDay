package karino2.livejournal.com.meatpieday.json;

import androidx.annotation.NonNull;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by _ on 2018/04/03.
 */

public class JsonNote {
    public List<JsonCell> cells;


    public static JsonNote fromJson(String buf) {
        Gson gson = getGson();
        return gson.fromJson(buf, JsonNote.class);
    }

    static Gson s_gson;
    @NonNull
    public static Gson getGson() {
        if(s_gson != null)
            return s_gson;
        s_gson =  new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        return s_gson;
    }

    public static JsonNote fromJson(InputStream is) throws IOException {
        // JsonReader reader = new JsonReader(new InputStreamReader(is, "UTF-8"));
        String json = readAll(is);
        return fromJson(json);
    }

    public static String readAll(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuffer buf = new StringBuffer();

        String line;
        while( (line = br.readLine()) != null ) {
            buf.append(line);
        }
        br.close();
        return buf.toString();
    }

}
