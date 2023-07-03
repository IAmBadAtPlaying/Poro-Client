package com.iambadatplaying.lcuHandler;

import org.json.JSONObject;

public class LootElement {
    private Integer count = null;
    private String disenchantLootName = null;
    private Integer disenchantValue = null;
    private String displayCategories = null;
    private String expiryTime = null;
    private Boolean isNew = null;
    private Boolean isRental = null;
    private String itemDesc = null;
    private String itemStatus = null;
    final private String lootId;
    private String lootName = null;
    private String rarity = null;
    private String redeemableStatus = null;
    private String splashPath = null;
    private String titlePath = null;
    private Integer storeItemId = null;
    private Integer upgradeEssenceValue = null;
    private Integer value = null;

    public static LootElement fromJsonObject(JSONObject jsonLootElement) {
        if(jsonLootElement == null) return null;
        LootElement lootElement = new LootElement(jsonLootElement.getString("lootId"));
        lootElement.setCount(jsonLootElement.getInt("count"));
        lootElement.setTitlePath(jsonLootElement.getString("tilePath"));
        lootElement.setLootName(jsonLootElement.getString("lootName"));
        lootElement.setDisplayCategories(jsonLootElement.getString("displayCategories"));
        if(jsonLootElement.has("itemDesc")) lootElement.setItemDesc(jsonLootElement.getString("itemDesc"));
        lootElement.setCount(jsonLootElement.getInt("count"));
        return lootElement;
    }

    public LootElement(String lootId) {
        this.lootId = lootId;
    }

    public Integer getCount() {
        return count;
    }

    public String getDisenchantLootName() {
        return disenchantLootName;
    }

    public Integer getDisenchantValue() {
        return disenchantValue;
    }

    public String getDisplayCategories() {
        return displayCategories;
    }

    public String getExpiryTime() {
        return expiryTime;
    }

    public Boolean getNew() {
        return isNew;
    }

    public Boolean getRental() {
        return isRental;
    }

    public String getItemDesc() {
        return itemDesc;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public String getLootId() {
        return lootId;
    }

    public String getLootName() {
        return lootName;
    }

    public String getRarity() {
        return rarity;
    }

    public String getRedeemableStatus() {
        return redeemableStatus;
    }

    public String getSplashPath() {
        return splashPath;
    }

    public Integer getStoreItemId() {
        return storeItemId;
    }

    public Integer getUpgradeEssenceValue() {
        return upgradeEssenceValue;
    }

    public Integer getValue() {
        return value;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public void setDisenchantLootName(String disenchantLootName) {
        this.disenchantLootName = disenchantLootName;
    }

    public void setDisenchantValue(Integer disenchantValue) {
        this.disenchantValue = disenchantValue;
    }

    public void setDisplayCategories(String displayCategories) {
        this.displayCategories = displayCategories;
    }

    public void setExpiryTime(String expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setNew(Boolean aNew) {
        isNew = aNew;
    }

    public void setRental(Boolean rental) {
        isRental = rental;
    }

    public void setItemDesc(String itemDesc) {
        this.itemDesc = itemDesc;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public void setLootName(String lootName) {
        this.lootName = lootName;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public void setRedeemableStatus(String redeemableStatus) {
        this.redeemableStatus = redeemableStatus;
    }

    public void setSplashPath(String splashPath) {
        this.splashPath = splashPath;
    }

    public void setStoreItemId(Integer storeItemId) {
        this.storeItemId = storeItemId;
    }

    public void setUpgradeEssenceValue(Integer upgradeEssenceValue) {
        this.upgradeEssenceValue = upgradeEssenceValue;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getTitlePath() {
        return titlePath;
    }

    public void setTitlePath(String titlePath) {
        this.titlePath = titlePath;
    }

    public static LootElement copyLow(LootElement src) {
        LootElement copy = new LootElement(src.getLootId());
        copy.setCount(src.getCount());
        copy.setItemDesc(src.getItemDesc());
        copy.setLootName(src.getLootName());
        copy.setValue(src.getValue());
        copy.setTitlePath(src.getTitlePath());
        copy.setSplashPath(src.getSplashPath());
        return copy;
    }
}
