package com.iambadatplaying.structs;

import org.json.JSONArray;
import org.json.JSONObject;

public class Queue implements Comparable<Queue> {
    private final Integer id;
    private String name;
    private String category;
    private String description;
    private String gameMode;
    private Boolean[] allowablePremadeSizes;

    public static Queue fromJsonObject(JSONObject jsonQueue) {
        if(jsonQueue == null ) {return null;}
        if(!jsonQueue.has("numPlayersPerTeam") || !jsonQueue.has("name")) {
            return null;
        }
        Boolean[] premadeSizes = new Boolean[jsonQueue.getInt("numPlayersPerTeam")];
        Queue queue = new Queue(jsonQueue.getInt("id"));
        queue.setName(jsonQueue.getString("name"));
        queue.setCategory(jsonQueue.getString("category"));
        queue.setGameMode(jsonQueue.getString("gameMode"));
        queue.setDescription(jsonQueue.getString("description"));
        queue.setQueueAvailability(jsonQueue.getString("queueAvailability"));
        //TODO: Fix this, it's not working
//        JSONArray jsonPremadeSizes = jsonQueue.getJSONArray("allowablePremadeSizes");
//        if(queue.getName() == null) {return null;}
//        for(int i = 0; i < jsonPremadeSizes.length(); i++) {
//            Integer index = jsonPremadeSizes.getInt(i) -1 ;
//            if(index < premadeSizes.length) {
//                premadeSizes[index] = true;
//            }
//        }
//        for (int i = 0; i < premadeSizes.length; i++) {
//            if(premadeSizes[i] == null ) {
//                premadeSizes[i] = false;
//            }
//        }
        queue.setAllowablePremadeSizes(premadeSizes);
        return queue;
    }

    public String getQueueAvailability() {
        return queueAvailability;
    }

    public void setQueueAvailability(String queueAvailability) {
        this.queueAvailability = queueAvailability;
    }

    private String queueAvailability;

    public Queue(Integer id) {
        this.id =id;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public Boolean[] getAllowablePremadeSizes() {
        return allowablePremadeSizes;
    }

    public void setAllowablePremadeSizes(Boolean[] allowablePremadeSizes) {
        this.allowablePremadeSizes = allowablePremadeSizes;
    }

    @Override
    public int compareTo(Queue o) {
        return Integer.compare(this.id,o.getId());
    }
}
