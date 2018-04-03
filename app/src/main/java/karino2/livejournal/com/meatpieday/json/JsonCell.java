package karino2.livejournal.com.meatpieday.json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Created by _ on 2018/04/03.
 */

public class JsonCell {

    String cellType;
    JsonElement source;
    // metadata
    // JsonElement metadata;

    public static class Metadata {
        public long updatedAt = 0;
    }
    public Metadata metadata;


    // always 1 element.
    List<Output> outputs;



    static Gson s_gson = new Gson();

    public String getSource() {
        return jsonElementToString(source);
    }


    static String jsonElementToString(JsonElement obj) {
        if(obj == null)
            return "";
        if(obj.isJsonArray()) {
            List<String> sources = s_gson.fromJson(obj, List.class);
            return mergeAll(sources);
        }
        return obj.getAsString();

    }


    static String mergeAll(List<String> texts) {
        StringBuilder buf = new StringBuilder();
        for(String source : texts) {
            buf.append(source);
            // should I handle return code here?
        }
        return buf.toString();
    }


    public static class Output {
        // (name, text) or data

        // name is deleted? just comment out for a while.
        // public String name = "";

        public String outputType;

        public JsonElement text;
        public Map<String, JsonElement> data;

        public Integer executionCount;

        public boolean isImage() {
            if(data == null)
                return false;
            for(String key : data.keySet()) {
                if(key.startsWith("image/png") ||
                        key.startsWith("image/jpeg")) {
                    return true;
                }
            }
            return false;
        }

        public void setResult(Integer newExecCount, JsonObject newData) {
            setData(newData);
            outputType = "execute_result";
            executionCount = newExecCount;
        }

        void setData(JsonObject newData) {
            Type dataType = new TypeToken<Map<String, JsonElement>>(){}.getType();
            data = s_gson.fromJson(newData, dataType);
        }

        public String getImageAsBase64() {
            for(String key : data.keySet()) {
                if(key.startsWith("image/png") ||
                        key.startsWith("image/jpeg")) {
                    return jsonElementToString(data.get(key));
                }
            }
            return null;
        }

        public String getText()
        {
            if(data == null)
                return jsonElementToString(text);
            return jsonElementToString(data.get("text/plain"));
        }

        public void appendResult(String newcontents) {
            // in this case, output is cleared at first and text must be JsonArray.
            JsonArray array = (JsonArray)text;
            array.add(newcontents);
        }

    }

    public String cellImageAsBase64() {
        return getOutput().getImageAsBase64();
    }

    Output getOutput() {
        if(outputs.isEmpty())
            return null;
        return outputs.get(0);
    }

    // Cell.java says:
    // 0: text(markdown), 1: image(image output with dummy code).
    public enum CellType {
        MARKDOWN(0),
        CODE(1),
        UNINITIALIZE(2);

        CellType(int id) {
            val= id;
        }
        public int val;
    }

    public CellType getCellType() {
        if("code".equals(cellType)) {
            return CellType.CODE;
        } else if("markdown".equals(cellType)) {
            return CellType.MARKDOWN;
        } else {
            return CellType.UNINITIALIZE;
        }
    }


}
