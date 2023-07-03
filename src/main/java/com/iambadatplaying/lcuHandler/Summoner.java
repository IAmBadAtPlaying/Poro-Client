package com.iambadatplaying.lcuHandler;

import javafx.scene.image.Image;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.math.BigInteger;

public class Summoner {
    private Integer accountId = null;
    private String displayName = null;
    private String internalName = null;
    private Integer percentCompleteForNextLevel = null;
    private Integer profileIconId = null;
    final private String puuid;
    private BigInteger summonerId = null;
    private Integer summonerLevel = null;
    private Integer xpSinceLastLevel = null;
    private Integer xpUntilLastLevel = null;
    private String firstPositionPreference = null;
    private String secondPositionPreference = null;
    private Image lobbyProfileIcon = null;
    private BufferedImage lobbyProfileRegalia = null;
    private String regaliaType = null;
    private String regaliaSelected = null;

    public static Summoner fromJsonObject(JSONObject jsonSummoner) {
        if(jsonSummoner == null) return null;
        Summoner summoner = new Summoner(jsonSummoner.getString("puuid"));
        summoner.setSummonerId(jsonSummoner.getBigInteger("summonerId"));
        summoner.setSummonerLevel(jsonSummoner.getInt("summonerLevel"));
        if(jsonSummoner.has("summonerName")) {
            summoner.setDisplayName(jsonSummoner.getString("summonerName"));
        }
        if(jsonSummoner.has("displayName")) {
            summoner.setDisplayName(jsonSummoner.getString("internalName"));
        }
        if(jsonSummoner.has("summonerInternalName")) {
            summoner.setInternalName(jsonSummoner.getString("summonerInternalName"));
        }
        if(jsonSummoner.has("internalName")) {
            summoner.setInternalName(jsonSummoner.getString("internalName"));
        }
        if(jsonSummoner.has("summonerIconId")) {
            summoner.setProfileIconId(jsonSummoner.getInt("summonerIconId"));
        }
        if(jsonSummoner.has("profileIconId")) {
            summoner.setProfileIconId(jsonSummoner.getInt("profileIconId"));
        }
        if(jsonSummoner.has("firstPositionPreference")) {
            summoner.setFirstPositionPreference(jsonSummoner.getString("firstPositionPreference"));
            summoner.setSecondPositionPreference(jsonSummoner.getString("secondPositionPreference"));
        }
        if(jsonSummoner.has("percentCompleteForNextLevel")) {
            summoner.setPercentCompleteForNextLevel(jsonSummoner.getInt("percentCompleteForNextLevel"));
        }
        return summoner;
    }

    public Summoner(String puuid) {
        this.puuid = puuid;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInternalName() {
        return internalName;
    }

    public Integer getProfileIconId() {
        return profileIconId;
    }

    public String getPuuid() {
        return puuid;
    }

    public BigInteger getSummonerId() {
        return summonerId;
    }

    public Integer getSummonerLevel() {
        return summonerLevel;
    }

    public Integer getXpSinceLastLevel() {
        return xpSinceLastLevel;
    }

    public Integer getXpUntilLastLevel() {
        return xpUntilLastLevel;
    }

    public Integer getPercentCompleteForNextLevel() {
        return percentCompleteForNextLevel;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public void setPercentCompleteForNextLevel(Integer percentCompleteForNextLevel) {
        this.percentCompleteForNextLevel = percentCompleteForNextLevel;
    }

    public void setProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }

    public void setSummonerId(BigInteger summonerId) {
        this.summonerId = summonerId;
    }

    public void setSummonerLevel(Integer summonerLevel) {
        this.summonerLevel = summonerLevel;
    }

    public void setXpSinceLastLevel(Integer xpSinceLastLevel) {
        this.xpSinceLastLevel = xpSinceLastLevel;
    }

    public void setXpUntilLastLevel(Integer xpUntilLastLevel) {
        this.xpUntilLastLevel = xpUntilLastLevel;
    }

    public String getFirstPositionPreference() {
        return firstPositionPreference;
    }

    public void setFirstPositionPreference(String firstPositionPreference) {
        this.firstPositionPreference = firstPositionPreference;
    }

    public String getSecondPositionPreference() {
        return secondPositionPreference;
    }

    public void setSecondPositionPreference(String secondPositionPreference) {
        this.secondPositionPreference = secondPositionPreference;
    }

    public Image getLobbyProfileIcon() {
        return lobbyProfileIcon;
    }

    public void setLobbyProfileIcon(Image lobbyProfileIcon) {
        this.lobbyProfileIcon = lobbyProfileIcon;
    }

    public BufferedImage getLobbyProfileRegalia() {
        return lobbyProfileRegalia;
    }

    public void setLobbyProfileRegalia(BufferedImage lobbyProfileRegalia) {
        this.lobbyProfileRegalia = lobbyProfileRegalia;
    }

    public String getRegaliaType() {
        return regaliaType;
    }

    public void setRegaliaType(String regaliaType) {
        this.regaliaType = regaliaType;
    }

    public String getRegaliaSelected() {
        return regaliaSelected;
    }

    public void setRegaliaSelected(String regaliaSelected) {
        this.regaliaSelected = regaliaSelected;
    }

    public Boolean equalPuuids(Summoner other) {
        if (other == null) {
            return false;
        }
        if (this.puuid == null) {
            return false;
        }
        return this.puuid.equals(other.puuid);
    }
}
