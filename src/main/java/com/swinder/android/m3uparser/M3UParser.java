package com.swinder.android.m3uparser;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class M3UParser {
    public static String TAG = "M3UParser";
    public interface M3UHandler {
        /**
         * When M3UParser get a M3UHead, this method will be called.
         *
         * @param header
         *            the instance of M3UHead.
         */
        boolean onSetEXTM3U(M3UHead header);
        /**
         * When M3UParser want to parse M3UHead(epg), this method will be called.
         *
         */
        boolean onReadEXTM3U();

        /**
         * When M3UParser get a M3UItem, this method will be called.
         *
         * @param item
         *            the instance of M3UItem.
         */
        boolean onReadEXTINF(M3UItem item);
    }

    private static final String PREFIX_EXTM3U = "#EXTM3U";
    private static final String PREFIX_EXTINF = "#EXTINF:";
    private static final String PREFIX_KODIPROP = "#KODIPROP:";
    private static final String PREFIX_EXTVLCOPT = "#EXTVLCOPT:";
    private static final String PREFIX_EXTHTTP = "#EXTHTTP:";
    private static final String PREFIX_COMMENT = "#";
    private static final String EMPTY_STRING = "";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_DLNA_EXTRAS = "dlna_extras";
    private static final String ATTR_PLUGIN = "plugin";
    private static final String ATTR_TVG_URL = "x-tvg-url";
    private static final String ATTR_CHANNEL_NAME = "channel_name";
    private static final String ATTR_DURATION = "duration";
    private static final String ATTR_LOGO = "logo";
    private static final String ATTR_ID = "id";
    private static final String ATTR_GROUP_TITLE = "group-title";
    private static final String ATTR_TVG_PREFIX = "tvg-";
    private static final String ATTR_TVG_SUFFIX = "-tvg";
    private static final String INVALID_STREAM_URL = "http://0.0.0.0:1234";

    private M3UItem mTempItem = null;

    /**
     * Use a specific handler to parse a m3u file.
     *
     * @param url
     *            a file to be parsed.
     * @param handler
     *            a specific handler which will not change the default handler.
     */
    public boolean parse(String url, M3UHandler handler, boolean parseFull) {
        Log.i(TAG, "> M3UParser parse(uhs) handler: " + handler);
        if (handler == null) { // No need do anything, if no handler.
            Log.i(TAG, "< M3UParser parse(uhs) false");
            return false;
        }
        Log.i(TAG, ". M3UParser parse(uhs) full: " + parseFull);
        boolean success = true;
        try {
            Log.i(TAG, ". M3UParser parse(uhs) url: " + url);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();
            Log.i(TAG, ". M3UParser parse(uhs) response.code: " + response.code());
            if (response.code() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        response.body().byteStream()));
                String tmp;
                while ((tmp = trim(br.readLine())) != null) {
                    //Log.i(TAG, "line: " + tmp);
                    try {
                        if (tmp.startsWith(PREFIX_EXTM3U) && (parseFull)) {
                            boolean sem = handler.onSetEXTM3U(parseHead(trim(tmp.replaceFirst(
                                    PREFIX_EXTM3U, EMPTY_STRING))));
                            if (!sem) {
                                Log.i(TAG, ". M3UParser parse(uhs) onSetEXTM3U false");
                            }
                            success &= sem;
                        } else if (tmp.startsWith(PREFIX_EXTINF)) {
                            // The old item must be committed when we meet a new item.
                            //flush(handler);
                            mTempItem = parseItem(trim(tmp.replaceFirst(
                                    PREFIX_EXTINF, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_KODIPROP)) {
                            mTempItem = parseKodiProp(trim(tmp.replaceFirst(
                                    PREFIX_KODIPROP, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_EXTVLCOPT)) {
                            mTempItem = parseExtVlcOpt(trim(tmp.replaceFirst(
                                    PREFIX_EXTVLCOPT, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_EXTHTTP)) {
                            mTempItem = parseExtHttp(trim(tmp.replaceFirst(
                                    PREFIX_EXTHTTP, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_COMMENT)) {
                            // Do nothing.
                        } else if (tmp.equals(EMPTY_STRING)) {
                            // Do nothing.
                        } else { // The single line is treated as the stream URL.
                            //Log.i(TAG, "updateURL: " + tmp);
                            updateURL(tmp);
                            boolean f = flush(handler);
                            if (!f) {
                                Log.i(TAG, ". M3UParser parse(uhs) flush false");
                            }
                            success &= f;
                        }
                    } catch (Exception ex) {
                        success = false;
                        Log.e(TAG, ". M3UParser parse(uhs) exception: " + ex.getMessage());
                    }
                }
                boolean r = flush(handler);
                if (!r) {
                    Log.i(TAG, ". M3UParser parse(uhs) flush 2 false");
                }
                success &= r;
                r = handler.onReadEXTM3U();
                if (!r) {
                    Log.i(TAG, ". M3UParser parse(uhs) onReadEXTM3U false");
                }
                success &= r;
                br.close();
            } else {
                Log.e(TAG, ". M3UParser parse(uhs) false, http request failed for " + url + " with error: " + response.message());
                success = false;
            }
        } catch (FileNotFoundException ex) {
            Log.e(TAG, ". M3UParser parse(uhs) file not found exception: " + ex.getMessage());
            ex.printStackTrace();
        } catch (IOException ex) {
            Log.e(TAG, ". M3UParser parse(uhs) io exception: " + ex.getMessage());
            ex.printStackTrace();
        }
        Log.i(TAG, "< M3UParser parse(uhs) success: " + success);
        return success;
    }

    private String trim(String str) {
        return str == null ? null : str.trim();
    }

    private boolean flush(M3UHandler handler) {
        boolean success = true;
        if (mTempItem != null) {
            // The invalid item must be skipped.
            if (mTempItem.getStreamURL() != null) {
                success = handler.onReadEXTINF(mTempItem);
            }
            mTempItem = null;
        }
        return success;
    }

    private void updateURL(String url) {
        //Log.i(TAG, ". M3UParser updateURL() mTempItem: " + mTempItem);
        if (mTempItem != null && !INVALID_STREAM_URL.equals(url)) {
            //Log.i(TAG, ". M3UParser updateURL() url: " + url);
            mTempItem.setStreamURL(url);
        }
    }

    private void putAttr(Map<String, String> map, String key, String value) {
        map.put(key, value);
    }

    private String getAttr(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null) {
            value = map.get(ATTR_TVG_PREFIX + key);
            if (value == null) {
                value = map.get(key + ATTR_TVG_SUFFIX);
            }
        }
        return value;
    }

    private M3UHead parseHead(String words) {
        Map<String, String> attr = parseAttributes(words);
        M3UHead header = new M3UHead();
        header.setName(getAttr(attr, ATTR_NAME));
        header.setType(getAttr(attr, ATTR_TYPE));
        header.setDLNAExtras(getAttr(attr, ATTR_DLNA_EXTRAS));
        header.setPlugin(getAttr(attr, ATTR_PLUGIN));
        header.setTVGUrl(getAttr(attr, ATTR_TVG_URL));
        return header;
    }

    private M3UItem parseItem(String words) {
        Map<String, String> attr = parseAttributes(words);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        item.setChannelName(getAttr(attr, ATTR_CHANNEL_NAME));
        item.setDuration(convert2int(getAttr(attr, ATTR_DURATION)));
        item.setLogoURL(getAttr(attr, ATTR_LOGO));
        String id = getAttr(attr, ATTR_ID);
        if (id == null) {
            id = getAttr(attr, "tvg-chno");
        }
        item.setChannelID(id);
        item.setGroupTitle(getAttr(attr, ATTR_GROUP_TITLE));
        item.setType(getAttr(attr, ATTR_TYPE));
        item.setDLNAExtras(getAttr(attr, ATTR_DLNA_EXTRAS));
        item.setPlugin(getAttr(attr, ATTR_PLUGIN));
        return item;
    }

    private M3UItem parseKodiProp(String words) {
        //Log.i(TAG, ". M3UParser parseKodiProp() words: " + words);
        Map<String, String> attr = parseAttributes(words);
        //Log.i(TAG, ". M3UParser parseKodiProp() attr: " + attr);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        item.setLicenseType(getAttr(attr, "inputstream.adaptive.license_type"));
        item.setLicenseKeyUrl(getAttr(attr, "inputstream.adaptive.license_key"));
        return item;
    }

    private M3UItem parseExtVlcOpt(String words) {
        //Log.i(TAG, ". M3UParser parseExtVlcOpt() words: " + words);
        Map<String, String> attr = parseAttributes(words);
        //Log.i(TAG, ". M3UParser parseExtVlcOpt() attr: " + attr);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        item.setUserAgent(getAttr(attr, "http-user-agent"));
        return item;
    }

    private M3UItem parseExtHttp(String words) {
        //Log.i(TAG, ". M3UParser parseExtHttp() words: " + words);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        try {
            JSONObject extHttp = new JSONObject(words);
            Map<String, String> header = new HashMap<>();

            Iterator<String> keysItr = extHttp.keys();
            while(keysItr.hasNext()) {
                String key = keysItr.next();
                String value = extHttp.getString(key);
                header.put(key, value);
            }
            item.setHttpHeader(header);
        } catch (Exception ex) {
            Log.e(TAG, ". M3UParser parseExtHttp() exception: " + ex.getMessage());
        }
        return item;
    }

    private Map<String, String> parseAttributes(String words) {
        Map<String, String> attr = new HashMap<>();
        if (words == null || words.equals(EMPTY_STRING)) {
            return attr;
        }
        Status status = Status.READY;
        String tmp = words;
        StringBuffer connector = new StringBuffer();
        int i = 0;
        char c = tmp.charAt(i);
        if (c == '-' || Character.isDigit(c)) {
            connector.append(c);
            while (++i < tmp.length()) {
                c = tmp.charAt(i);
                if (Character.isDigit(c)) {
                    connector.append(c);
                } else {
                    break;
                }
            }
            putAttr(attr, ATTR_DURATION, connector.toString());
            tmp = trim(tmp.replaceFirst(connector.toString(), EMPTY_STRING));
            reset(connector);
            i = 0;
        }
        String key = EMPTY_STRING;
        boolean startWithQuota = false;
        while (i < tmp.length()) {
            c = tmp.charAt(i++);
            switch (status) {
                case READY:
                    if (Character.isWhitespace(c)) {
                        // Do nothing
                    } else if (c == ',') {
                        putAttr(attr, ATTR_CHANNEL_NAME, tmp.substring(i));
                        i = tmp.length();
                    } else {
                        connector.append(c);
                        status = Status.READING_KEY;
                    }
                    break;
                case READING_KEY:
                    if (c == '=') {
                        key = trim(key + connector);
                        reset(connector);
                        status = Status.KEY_READY;
                    } else {
                        connector.append(c);
                    }
                    break;
                case KEY_READY:
                    if (!Character.isWhitespace(c)) {
                        if (c == '"') {
                            startWithQuota = true;
                        } else {
                            connector.append(c);
                        }
                        status = Status.READING_VALUE;
                    }
                    break;
                case READING_VALUE:
                    if (startWithQuota) {
                        if (c != '"') {
                            connector.append(c);
                            int end = tmp.indexOf("\"", i);
                            end = end == -1 ? tmp.length() : end;
                            connector.append(tmp.substring(i, end));
                            startWithQuota = false;
                            i = end + 1;
                        }
                        putAttr(attr, key, connector.toString());
                        reset(connector);
                        key = EMPTY_STRING;
                        status = Status.READY;
                        break;
                    }
                    if (Character.isWhitespace(c) && tmp.substring(i).contains("=")) {
                        if (connector.length() > 0) {
                            putAttr(attr, key, connector.toString());
                            reset(connector);
                        }
                        key = EMPTY_STRING;
                        status = Status.READY;
                    } else {
                        connector.append(c);
                    }
                    break;
                default:
                    break;
            }
        }
        if (!key.equals(EMPTY_STRING) && connector.length() > 0) {
            putAttr(attr, key, connector.toString());
            reset(connector);
        }
        return attr;
    }

    private int convert2int(String value) {
        int ret;
        try {
            ret = Integer.parseInt(value);
        } catch (Exception e) {
            ret = -1;
        }
        return ret;
    }

    private void reset(StringBuffer buffer) {
        buffer.delete(0, buffer.length());
    }

    private enum Status {
        READY, READING_KEY, KEY_READY, READING_VALUE,
    }
}
