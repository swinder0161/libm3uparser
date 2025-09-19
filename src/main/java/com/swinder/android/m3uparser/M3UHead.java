package com.swinder.android.m3uparser;

import androidx.annotation.NonNull;

public class M3UHead {
    /**
     * The human readable playlist name.
     */
    private String mName;
    /**
     * The default for playlist media type.
     */
    private String mType;
    /**
     * The default for playlist DLNA profile.
     */
    private String mDLNAExtras;
    /**
     * The default for playlist media plugin (handler).
     */
    private String mPlugin;
    private String mTVGUrl;

    public void setName(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setType(String type) {
        mType = type;
    }

    public void setDLNAExtras(String profile) {
        mDLNAExtras = profile;
    }

    public void setPlugin(String plugin) {
        mPlugin = plugin;
    }

    public void setTVGUrl(String url) {
        mTVGUrl = url;
    }

    public String getTVGUrl() {
        return mTVGUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "[M3UHead@" + Integer.toHexString(hashCode()) + "]: Name: " + mName + ", Type: " +
                mType + ", DLNA Extras: " + mDLNAExtras + ", Plugin: " + mPlugin + ", TVGUrl: " +
                mTVGUrl;
    }
}