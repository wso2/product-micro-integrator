package org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.schema;

import com.google.gson.JsonObject;
import org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.moesif.MoesifConstants;

public class MoesifDataSchema {
    private final String actionName;
//    private String transactionId;
    private final MoesifDataSchemaElement request;
    private final MoesifDataSchemaElement metadata;

    public MoesifDataSchema(String actionName, MoesifDataSchemaElement request, MoesifDataSchemaElement metadata) {
        this.actionName = actionName;
        this.request = request;
        this.metadata = metadata;
    }

    public JsonObject getJsonObject() {
        JsonObject exportingAnalytic = new JsonObject();
        exportingAnalytic.addProperty(MoesifConstants.ACTION_NAME, actionName);
        exportingAnalytic.add(MoesifConstants.REQUEST, request.toJsonObject());
        exportingAnalytic.add(MoesifConstants.METADATA, metadata.toJsonObject());

        return exportingAnalytic;
    }
}
