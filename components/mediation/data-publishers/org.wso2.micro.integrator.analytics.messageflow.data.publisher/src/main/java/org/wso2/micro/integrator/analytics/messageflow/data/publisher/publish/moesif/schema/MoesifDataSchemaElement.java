package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class MoesifDataSchemaElement {
    private final Map<String, Object> attributes = new HashMap<>();

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            if (entry.getValue() instanceof String) {
                json.addProperty(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                json.addProperty(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Double) {
                json.addProperty(entry.getKey(), (Double) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                json.addProperty(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                json.addProperty(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof Short) {
                json.addProperty(entry.getKey(), (Short) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                json.addProperty(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof JsonObject) {
                json.add(entry.getKey(), (JsonObject) entry.getValue());
            } else if (entry.getValue() instanceof MoesifDataSchemaElement) {
                json.add(entry.getKey(), ((MoesifDataSchemaElement) entry.getValue()).toJsonObject());
            }
        }
        return json;
    }
}
