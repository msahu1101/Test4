package com.mgm.payments.processing.service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mgm.payments.processing.service.enums.LogMarker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class LogMaskingConverter {

    private LogMaskingConverter() {
    }

    /**
     * Group index
     */
    private static final String MASK_CHAR = "*";
    private static final String REGEX_DELIMITER = "|";

    private static final List<String> maskKeys = Stream.of(
            "city", "country", "postalCode", "profile", "state", "zip", "zipCode",
            "region", "nameOnCard", "email", "cardHolderName", "firstName", "lastName", "expiryMonth", "expiryYear",
            "expiryDate", "expireMonth", "expireYear", "address", "address2", "nameOnTender", "securityCode", "authorization",
            "phoneNumber", "creditCardHolderName", "tenderDisplay", "maskedCardNumber").collect(Collectors.toList());


    private static final String JSON_PATTERN_REGEX = "\"(%s)\":\\s*\"*([^\"]+)\"*([,}])+";
    private static final String TEXT_PATTERN_REGEX = "s*(%s)=\\s*\"*([^,\"]+)\"*([,}\\n])+";// QuotedText
    // Map to hold the pattern for masking
    private static final Map<String, Pattern> patternMap = initializePatternMap();

    private static Map<String, Pattern> initializePatternMap() {
        Map<String, Pattern> map = new HashMap<>();
        map.put(JSON_PATTERN_REGEX, Pattern.compile(String.format(JSON_PATTERN_REGEX, String.join(REGEX_DELIMITER, maskKeys))));
        map.put(TEXT_PATTERN_REGEX, Pattern.compile(String.format(TEXT_PATTERN_REGEX, String.join(REGEX_DELIMITER, maskKeys))));
        return map;
    }

    /**
     * Mask sensitive data from json object
     *
     * @param message
     * @return
     */
    public static String mask(Object message) {
        return mask(objectToJson(message), LogMarker.JSON);
    }

    private static String objectToJson(Object source) {
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            log.error("Util objectMapper Exception for object to json :: {}", e.toString());
        }
        return StringUtils.EMPTY;
    }


    /**
     * Mask sensitive data from POJO, JSON or TEXT data string.
     *
     * @param message
     * @param marker
     * @return
     */
    public static String mask(String message, LogMarker marker) {
        StringBuilder outputMessage = new StringBuilder();
        // Do nothing if there is no message
        if (message == null || message.trim().isEmpty()) {
            outputMessage.append(message);
            return outputMessage.toString();
        }
        // Default marker is TEXT if not provided.
        String markerName = marker != null ? marker.name() : LogMarker.JSON.name();
        Pattern pattern = patternMap.get(TEXT_PATTERN_REGEX);
        try {
            if (markerName.equalsIgnoreCase(LogMarker.JSON.name())) {
                pattern = patternMap.get(JSON_PATTERN_REGEX);
            } else if (markerName.equalsIgnoreCase(LogMarker.TEXT.name())
                    || markerName.equalsIgnoreCase(LogMarker.POJO.name())) {
                pattern = patternMap.get(TEXT_PATTERN_REGEX);
            } else if (markerName.equalsIgnoreCase(LogMarker.NONE.name())) {
                pattern = null;
            } else {
                //Default is text pattern masking.
                pattern = patternMap.get(TEXT_PATTERN_REGEX);
            }
            //apply mask if valid marker found, else return as is.
            if (pattern != null) {
                outputMessage.append(applyMask(pattern, message, markerName));
            } else {
                outputMessage.append(message);
            }
        } catch (Exception e) {
            outputMessage.append(message);
        }
        //Return the final masked string
        return outputMessage.toString();
    }


    /**
     * Mask the message in the requested format.
     *
     * @param pattern
     * @param message
     * @param markerName
     * @return
     */
    @SuppressWarnings({
            "java:S3776"
            ,
            "java:S1854"
    })
    private static String applyMask(Pattern pattern, String message, String markerName) {
        if (markerName.equalsIgnoreCase(LogMarker.TEXT.name()) && !message.endsWith(",")) {
            message += ",";
        }
        StringBuilder buffer = new StringBuilder();
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            StringBuilder valueAfterLastQuote = new StringBuilder();
            String matcherGroup=matcher.group();
            String newValue = "";
            int valueLength = value.length();
            // pci data make empty
            if (key.equalsIgnoreCase("securityCode")) {
                newValue = "";
            } else {
                // //skip masking Address object or null value
                newValue = getValue(value, key, valueLength);
                if (newValue == null) continue;

            }

            //closing braces adding
            if (matcherGroup.contains("}") || matcherGroup.contains("]")) {
                char[] closeBraces = {'}', ']'};
                for (char brace : closeBraces) {
                    long braceCount = matcherGroup.chars().filter(ch -> ch == brace).count();
                    valueAfterLastQuote.append(StringUtils.repeat(brace, (int) (braceCount)));
                }

            }
            if (valueAfterLastQuote.length() == 0) {
                matcher.appendReplacement(buffer, "\"" + key + "\":\"" + newValue + "\",");
            } else {
                matcher.appendReplacement(buffer, "\"" + key + "\":\"" + newValue + "\"" + valueAfterLastQuote + ",");
            }


        } // while
        matcher.appendTail(buffer);
        // Remove the last comma if exists before returning.
        if (buffer.charAt(buffer.length() - 1) == ',') {
            buffer.deleteCharAt(buffer.length() - 1);
        }


        return buffer.toString();
    }

    private static @Nullable String getValue(String value, String key, int valueLength) {
        String newValue;
        if (value.equalsIgnoreCase("null") || (key.equalsIgnoreCase("Address") && value.contains("{"))) {
            return null;
        }
        if(key.equalsIgnoreCase("maskedCardNumber") ||
                key.equalsIgnoreCase("tenderDisplay")){
            int maskLength = valueLength - 4;
            return StringUtils.repeat(MASK_CHAR, maskLength) + value.substring(maskLength);
        }
        if (valueLength > 2) {
            newValue = value.charAt(0) + StringUtils.repeat(MASK_CHAR, valueLength - 2)
                    + value.charAt(valueLength - 1);
        } else {
            newValue = StringUtils.repeat(MASK_CHAR, valueLength);
        }
        return newValue;
    }
}