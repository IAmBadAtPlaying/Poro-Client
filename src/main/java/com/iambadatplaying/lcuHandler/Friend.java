package com.iambadatplaying.lcuHandler;

import org.json.JSONObject;

import java.math.BigInteger;

public class Friend implements Comparable<Friend> {
    private final String puuid;
    private Integer displayGroupId = null;
    private String gameName = null;
    private String name = null;
    private String gameTag = null;
    private Integer icon = null;
    private String id = null;
    private BigInteger summonerId = null;
    private String availability = null;
    private String pid = null;
    private String note = null;
    private String statusMessage = null;
    private JSONObject lol = null;

    public static Friend fromJsonObject(JSONObject jsonObject) {
        Friend friend = new Friend(jsonObject.getString("puuid"));
        friend.setSummonerId(jsonObject.getBigInteger("summonerId"));
        friend.setGameTag(jsonObject.getString("gameTag"));
        friend.setIcon(jsonObject.getInt("icon"));
        friend.setGameName(jsonObject.getString("gameName"));
        friend.setAvailability(jsonObject.getString("availability"));
        friend.setDisplayGroupId(jsonObject.getInt("displayGroupId"));
        friend.setLol(jsonObject.getJSONObject("lol"));
        friend.setPid(jsonObject.getString("pid"));
        friend.setNote(jsonObject.getString("note"));
        friend.setName(jsonObject.getString("name"));
        friend.setStatusMessage(jsonObject.getString("statusMessage"));
        return friend;
    }

    public Friend(String puuid) {
        this.puuid = puuid;
    }

    public String getPuuid() {
        return puuid;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public Integer getDisplayGroupId() {
        return displayGroupId;
    }

    public void setDisplayGroupId(Integer displayGroupId) {
        this.displayGroupId = displayGroupId;
    }
    public String getGameTag() {
        return gameTag;
    }

    public void setGameTag(String gameTag) {
        this.gameTag = gameTag;
    }

    public Integer getIcon() {
        return icon;
    }

    public void setIcon(Integer icon) {
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigInteger getSummonerId() {
        return summonerId;
    }

    public void setSummonerId(BigInteger summonerId) {
        this.summonerId = summonerId;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public JSONObject getLol() {
        return lol;
    }

    public void setLol(JSONObject lol) {
        this.lol = lol;
    }

    @Override
    public int compareTo(Friend o) {
        return String.CASE_INSENSITIVE_ORDER.compare(this.availability, o.availability);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
