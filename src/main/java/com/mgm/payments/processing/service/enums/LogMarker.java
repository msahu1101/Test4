package com.mgm.payments.processing.service.enums;

public enum LogMarker {

    JSON ("JSON-MASK"),
    TEXT ("TEXT-MASK"),
    NONE ("NONE"),
    POJO ("POJO-MASK");

    private String markerValue;

    LogMarker(String marker) {
        this.markerValue = marker;
    }


    public String getValue() {
        return this.markerValue;
    }

    public LogMarker getMarker(String markerVal) {
        for (LogMarker temp : values()) {
            if(temp.getValue().equalsIgnoreCase(markerVal)) {
                return temp;
            }
        }
        return null;
    }

}
