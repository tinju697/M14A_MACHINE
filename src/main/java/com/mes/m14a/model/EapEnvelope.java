package com.mes.m14a.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outer envelope of every EapApi request and response.
 *
 * Shape (per API_Protocol_Reference):
 * <pre>
 * {
 *   "Hwd":           "<guid token or empty>",
 *   "Indicator":     "QUERY_RECORD | ADD_RECORD | ADD_ATTR",
 *   "SerializeData": "<escaped JSON string of EapPayload>",
 *   "Display":       "<server message, response only>"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EapEnvelope {

    @JsonProperty("Hwd")           public String hwd;
    @JsonProperty("Indicator")     public String indicator;
    @JsonProperty("SerializeData") public String serializeData;
    @JsonProperty("Display")       public String display;

    public EapEnvelope() {}

    public EapEnvelope(String hwd, String indicator, String serializeData) {
        this.hwd = hwd;
        this.indicator = indicator;
        this.serializeData = serializeData;
    }
}
