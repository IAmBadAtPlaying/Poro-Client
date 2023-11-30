package com.iambadatplaying;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Optional;

public class Util {

    public static boolean jsonKeysPresent(JSONObject jsonObject, String... attributes) {
        if (jsonObject == null || attributes == null) return false;
        for (String attribute : attributes) {
            if (!jsonObject.has(attribute)) return false;
        }
        return true;
    }

    public static void copyJsonAttrib(String key, JSONObject src, JSONObject dst) {
        if (src == null || dst == null) return;
        doCopyAttrib(key, src, dst);
    }

    private static void doCopyAttrib(String key, JSONObject src, JSONObject dst) {
        if (src.has(key)) {
            Object object = src.get(key);
            if (object != null) {
                dst.put(key, object);
            }
        }
    }

    public static void copyJsonAttributes(JSONObject src, JSONObject dst, String... attributes) {
        if (src == null || dst == null || attributes == null) return;
        for (String attribute : attributes) {
            doCopyAttrib(attribute, src, dst);
        }
    }

    public static Optional<Integer> getInteger(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key)) return Optional.empty();
        return Optional.of(jsonObject.getInt(key));
    }

    public static Optional<String> getString(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key)) return Optional.empty();
        return Optional.of(jsonObject.getString(key));
    }

    public static Optional<Boolean> getBoolean(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key)) return Optional.empty();
        return Optional.of(jsonObject.getBoolean(key));
    }

    public static Optional<JSONObject> getJSONObject(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key)) return Optional.empty();
        return Optional.of(jsonObject.getJSONObject(key));
    }

    public static Optional<JSONArray> getJSONArray(JSONObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key)) return Optional.empty();
        return Optional.of(jsonObject.getJSONArray(key));
    }

    private Util() {}
}
