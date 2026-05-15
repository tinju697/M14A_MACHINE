package com.mes.m14a.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inner payload, JSON-serialized into the SerializeData string of {@link EapEnvelope}.
 *
 * <pre>
 * {
 *   "LineCode":       "...",
 *   "SectionCode":    "...",
 *   "StationCode":    <int or string>,
 *   "OPCategory":     "LOGIN | BIND | UNIT_PROCESS_CHECK | UNIT_PROCESS_COMMIT",
 *   "OPRequestInfo":  { ... varies per op ... },
 *   "OPResponseInfo": { ... }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EapPayload {

    @JsonProperty("LineCode")       public String lineCode = "";
    @JsonProperty("SectionCode")    public String sectionCode = "";
    @JsonProperty("StationCode")    public Object stationCode;
    @JsonProperty("OPCategory")     public String opCategory;
    @JsonProperty("OPRequestInfo")  public Map<String, Object> opRequestInfo = new LinkedHashMap<>();
    @JsonProperty("OPResponseInfo") public Map<String, Object> opResponseInfo = new LinkedHashMap<>();

    public EapPayload() {}

    public EapPayload(String lineCode, String sectionCode, Object stationCode, String opCategory) {
        this.lineCode = lineCode == null ? "" : lineCode;
        this.sectionCode = sectionCode == null ? "" : sectionCode;
        this.stationCode = stationCode;
        this.opCategory = opCategory;
    }

    public EapPayload put(String key, Object value) {
        opRequestInfo.put(key, value);
        return this;
    }

    public String responseString(String key) {
        Object v = opResponseInfo == null ? null : opResponseInfo.get(key);
        return v == null ? "" : v.toString();
    }
}
